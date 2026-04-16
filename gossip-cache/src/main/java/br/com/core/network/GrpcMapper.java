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
    
    public AppRequestProto requestToGrpc(AppRequest request) { 
        
        AppRequestProto.Builder requestGrpcBuilder = AppRequestProto.newBuilder();

        // 🔥 PROTEÇÃO CONTRA NULLS (Essencial para os Heartbeats)
        if (request.getOperation() != null) {
            requestGrpcBuilder.setOperation(request.getOperation().toString());
        }
        
        if (request.getKey() != null) {
            requestGrpcBuilder.setKey(request.getKey());
        }

        if (request.getValue() != null) {
            ByteString convertBytes = ByteString.copyFrom(request.getValue());
            requestGrpcBuilder.setValue(convertBytes);
        }

        return requestGrpcBuilder.build();
    }

    public AppRequest grpcToRequest(AppRequestProto grpcRequest) { 

        Operation operation = null;
        if (!grpcRequest.getOperation().isEmpty()) {
            operation = Operation.valueOf(grpcRequest.getOperation());
        }
        
        String key = grpcRequest.getKey().isEmpty() ? null : grpcRequest.getKey();
        ByteString bytes = grpcRequest.getValue();

        byte[] convertBytes = bytes.isEmpty() ? null : bytes.toByteArray();

        return new AppRequest(operation, key, convertBytes);
    }

    public AppResponseProto responseToGrpc(AppResponse response) {

        AppResponseProto.Builder responseGrpcBuilder = AppResponseProto.newBuilder();

        if (response.getStatus() != null) {
            responseGrpcBuilder.setStatus(response.getStatus());
        }
        
        if (response.getMessage() != null) {
            responseGrpcBuilder.setMessage(response.getMessage());
        }

        if (response.getValue() != null) {
            ByteString convertBytes = ByteString.copyFrom(response.getValue());
            responseGrpcBuilder.setValue(convertBytes);
        }

        return responseGrpcBuilder.build();
    }

    public AppResponse grpcToResponse(AppResponseProto grpcResponse) {

        String status = grpcResponse.getStatus().isEmpty() ? null : grpcResponse.getStatus();
        String message = grpcResponse.getMessage().isEmpty() ? null : grpcResponse.getMessage();
        ByteString bytes = grpcResponse.getValue();

        byte[] convertBytes = bytes.isEmpty() ? null : bytes.toByteArray();

        return new AppResponse(status, convertBytes, message);
    }

    public GossipMessageProto gossipToGrpc(GossipMessage gossip) {

        AppRequestProto requestProto = requestToGrpc(gossip.getData());

        NodeInfoProto.Builder grpcNodeBuilder = NodeInfoProto.newBuilder()
            .setPort(gossip.getSourceNode().getPort());
            
        if (gossip.getSourceNode().getAddress() != null) {
            grpcNodeBuilder.setAddress(gossip.getSourceNode().getAddress());
        }
        if (gossip.getSourceNode().getSequenceNumber() != null) {
            grpcNodeBuilder.setId(gossip.getSourceNode().getSequenceNumber().toString());
        }

        GossipMessageProto.Builder grpcGossipBuilder = GossipMessageProto.newBuilder()
            .setHopCount(gossip.getHopCount())
            .setData(requestProto)
            .setSourceNode(grpcNodeBuilder.build());

        if (gossip.getSequenceNumber() != null) {
            grpcGossipBuilder.setSequenceNumber(gossip.getSequenceNumber().toString());
        }

        return grpcGossipBuilder.build();
    }

    public GossipMessage grpcToGossip(GossipMessageProto grpcGossip) {

        AppRequest request = grpcToRequest(grpcGossip.getData());

        NodeInfo node = new NodeInfo(
            UUID.fromString(grpcGossip.getSourceNode().getId()), 
            grpcGossip.getSourceNode().getAddress(), 
            grpcGossip.getSourceNode().getPort(), 
            System.currentTimeMillis()
        );

        return new GossipMessage(
            node, 
            UUID.fromString(grpcGossip.getSequenceNumber()), 
            request, 
            grpcGossip.getHopCount()
        );
    }
}
