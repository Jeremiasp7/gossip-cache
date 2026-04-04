package br.com.core.network;

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

    public GrpcStrategy(RequestHandler handler, GrpcMapper grpcMapper) {
        this.handler = handler;
        this.grpcMapper = grpcMapper;
    }

    @Override
    public void startListening(int port) { // grpc protocol for listening requests

        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.submit(() -> { // only a one thread, the grpc manage the rest asynchronously
                    
            try { // a server is created and he listen in a determinate port
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
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) { // the way that grpc send requests

        ManagedChannel channel = null;

        try { // a channel is created for send the requests; when a request comes, a response is send
            channel = ManagedChannelBuilder.forAddress(
                destinationNode.getAddress(), 
                destinationNode.getPort()
            ).usePlaintext().build();

            NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);

            AppRequestProto requestProto = grpcMapper.requestToGrpc(request);
            AppResponseProto responseProto = stub.processRequest(requestProto);

            AppResponse response = grpcMapper.grpcToResponse(responseProto);

            return response;

        } catch (StatusRuntimeException e) {
            System.err.println("Falha na comunicação gRPC com o nó " + destinationNode.getPort() + ": " + e.getStatus().getDescription());
            
            return new AppResponse("503", null, "Service Unavailable");
        }  finally {
            if (channel != null && !channel.isShutdown()) {
                channel.shutdown();
            }
        }
    }

    @Override
    public void sendGossip(GossipMessage message, NodeInfo destinationNode) { // the way that grpc send gossips

        final ManagedChannel channel = ManagedChannelBuilder.forAddress(
                                            destinationNode.getAddress(), 
                                            destinationNode.getPort()
                                        ).usePlaintext().build();

        try { // again, a channel is created for the communication; but here, doesn't matter is the node receive the message
            
            NodeServiceGrpc.NodeServiceStub stub = NodeServiceGrpc.newStub(channel);

            GossipMessageProto gossipProto = grpcMapper.gossipToGrpc(message);

            stub.processGossip(gossipProto, new StreamObserver<Empty>() { // the channel is closed only if the communication ends
                @Override
                public void onNext(Empty value) {}

                @Override
                public void onError(Throwable t) {
                    channel.shutdown();
                }

                @Override
                public void onCompleted() {
                    channel.shutdown();
                }
            });

        } catch (Exception e) {
            System.err.println("Erro interno ao preparar fofoca: " + e.getMessage());
            if (channel != null && !channel.isShutdown()) {
                channel.shutdown();
            }
        } 
    } 
    
}
