package br.com.core.network;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.RequestHandler;
import br.com.grpc.AppRequestProto;
import br.com.grpc.AppResponseProto;
import br.com.grpc.Empty;
import br.com.grpc.GossipMessageProto;
import br.com.grpc.NodeServiceGrpc;
import io.grpc.stub.StreamObserver;

public class NodeServiceImpl extends NodeServiceGrpc.NodeServiceImplBase {

    private RequestHandler handler;

    private GrpcMapper gprcMapper;
    
    public NodeServiceImpl(RequestHandler handler, GrpcMapper gprcMapper) {
        this.handler = handler;
        this.gprcMapper = gprcMapper;
    }

    @Override
    public void processRequest (AppRequestProto requestProto, StreamObserver<AppResponseProto> responseObserver) {

        try {
            AppRequest request = gprcMapper.grpcToRequest(requestProto);
            AppResponse response = handler.handleRequest(request);

            AppResponseProto responseProto = gprcMapper.responseToGrpc(response);

            responseObserver.onNext(responseProto);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void processGossip (GossipMessageProto gossipProto, StreamObserver<Empty> responseObserver) {

        try {
            GossipMessage gossip = gprcMapper.grpcToGossip(gossipProto);

            handler.handleGossip(gossip);

            responseObserver.onNext(br.com.grpc.Empty.newBuilder().build());
            responseObserver.onCompleted();

        }catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}