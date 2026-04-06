package br.com.core.network;

import java.util.UUID;

import com.google.protobuf.ByteString;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.Operation;
import br.com.grpc.AppRequestProto;
import br.com.grpc.AppResponseProto;
import br.com.grpc.GossipMessageProto;
import br.com.grpc.NodeInfoProto;

public class GrpcMapper {
    
    public AppRequestProto requestToGrpc(AppRequest request) { // converting a app request in a app request proto, for grpc
        
        AppRequestProto.Builder requestGrpcBuilder = AppRequestProto.newBuilder();

        requestGrpcBuilder.setOperation(request.getOperation().toString());
        requestGrpcBuilder.setKey(request.getKey());

        if (request.getValue() != null) {
            ByteString convertBytes = ByteString.copyFrom(request.getValue());
            requestGrpcBuilder.setValue(convertBytes);
        }

        AppRequestProto grpcRequest = requestGrpcBuilder.build();

        return grpcRequest;
    }

    public AppRequest grpcToRequest(AppRequestProto grpcRequest) { // converting a app request proto grpc in a app request

        Operation operation = Operation.valueOf(grpcRequest.getOperation());
        String key = grpcRequest.getKey();
        ByteString bytes = grpcRequest.getValue();

        byte[] convertBytes = bytes.isEmpty() ? null: bytes.toByteArray();

        AppRequest request = new AppRequest(operation, key, convertBytes);

        return request;
    }

    public AppResponseProto responseToGrpc(AppResponse response) {

        AppResponseProto.Builder responseGrpcBuilder = AppResponseProto.newBuilder();

        responseGrpcBuilder.setStatus(response.getStatus());
        responseGrpcBuilder.setMessage(response.getMessage());

        if (response.getValue() != null) {
            ByteString convertBytes = ByteString.copyFrom(response.getValue());
            responseGrpcBuilder.setValue(convertBytes);
        }

        AppResponseProto grpcResponse = responseGrpcBuilder.build();

        return grpcResponse;
    }

    public AppResponse grpcToResponse(AppResponseProto grpcResponse) {

        String status = grpcResponse.getStatus();
        String message = grpcResponse.getMessage();
        ByteString bytes = grpcResponse.getValue();

        byte[] convertBytes = bytes.isEmpty() ? null: bytes.toByteArray();

        AppResponse response = new AppResponse(status, convertBytes, message);

        return response;
    }

    public GossipMessageProto gossipToGrpc(GossipMessage gossip) {

        AppRequestProto requestProto = requestToGrpc(gossip.getData());

        NodeInfoProto grpcNode = NodeInfoProto.newBuilder()
            .setAddress(gossip.getSourceNode().getAddress())
            .setPort(gossip.getSourceNode().getPort())
            .setId(gossip.getSourceNode().getSequenceNumber().toString())
            .build();

        GossipMessageProto grpcGossip = GossipMessageProto.newBuilder()
            .setSequenceNumber(gossip.getSequenceNumber().toString())
            .setHopCount(gossip.getHopCount())
            .setData(requestProto)
            .setSourceNode(grpcNode)
            .build();

        return grpcGossip;
    }

    public GossipMessage grpcToGossip(GossipMessageProto grpcGossip) {

        AppRequest request = grpcToRequest(grpcGossip.getData());

        NodeInfo node = new NodeInfo(
            UUID.fromString(grpcGossip.getSourceNode().getId()), 
            grpcGossip.getSourceNode().getAddress(), 
            grpcGossip.getSourceNode().getPort(), 
            System.currentTimeMillis()
        );

        GossipMessage gossip = new GossipMessage(
            node, 
            UUID.fromString(grpcGossip.getSequenceNumber()), 
            request, 
            grpcGossip.getHopCount()
        );

        return gossip;
    }

}
