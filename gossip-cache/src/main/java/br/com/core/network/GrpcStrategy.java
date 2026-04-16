package br.com.core.network;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;
import br.com.grpc.AppRequestProto;
import br.com.grpc.AppResponseProto;
import br.com.grpc.Empty;
import br.com.grpc.GossipMessageProto;
import br.com.grpc.NodeServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class GrpcStrategy implements CommunicationStrategy {
    
    private RequestHandler handler;
    private GrpcMapper grpcMapper;

    private ConcurrentHashMap<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();

    public GrpcStrategy(RequestHandler handler, GrpcMapper grpcMapper) {
        this.handler = handler;
        this.grpcMapper = grpcMapper;
    }

    private ManagedChannel getOrCreateChannel(NodeInfo node) { // open the channel only once and send all for him
        String key = node.getAddress() + ":" + node.getPort();
        return channelCache.computeIfAbsent(key, k -> 
            ManagedChannelBuilder.forAddress(node.getAddress(), node.getPort())
                .usePlaintext()
                .build()
        );
    }

    @Override
    public void startListening(int port) { 

        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.submit(() -> { 
            try { 
                Server server = ServerBuilder.forPort(port)
                    .addService(new NodeServiceImpl(handler, grpcMapper))
                    .build();
                    
                server.start();
                System.out.println("Servidor gRPC escutando na porta: " + port);

                server.awaitTermination(); 
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) { // blocking thread

        ManagedChannel channel = getOrCreateChannel(destinationNode);

        try { 
            NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);

            AppRequestProto requestProto = grpcMapper.requestToGrpc(request);
            AppResponseProto responseProto = stub.processRequest(requestProto);

            return grpcMapper.grpcToResponse(responseProto);

        } catch (StatusRuntimeException e) {
            System.err.println("Falha na comunicação gRPC com o nó " + destinationNode.getPort() + ": " + e.getStatus().getDescription());
            return new AppResponse("404", null, "Not Found");
        } 
    }

    @Override
    public void sendGossip(GossipMessage message, NodeInfo destinationNode) { // gossip without blocking

        ManagedChannel channel = getOrCreateChannel(destinationNode);

        try { 
            NodeServiceGrpc.NodeServiceStub stub = NodeServiceGrpc.newStub(channel);

            GossipMessageProto gossipProto = grpcMapper.gossipToGrpc(message);

            stub.processGossip(gossipProto, new StreamObserver<Empty>() { 
                @Override
                public void onNext(Empty value) {}

                @Override
                public void onError(Throwable t) {
                    System.err.println("Aviso gRPC: Falha na fofoca silenciosa - " + t.getMessage());
                }

                @Override
                public void onCompleted() {
                }
            });

        } catch (Exception e) {
            System.err.println("Erro interno ao preparar fofoca: " + e.getMessage());
        } 
    } 
}
