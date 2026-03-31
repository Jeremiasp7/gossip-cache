package network;

import model.AppRequest;
import model.AppResponse;
import model.GossipMessage;
import model.NodeInfo;

public interface CommunicationStrategy {
    
    public void startListening(int port); // start the server for listening the requests in a port
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode); // send a request for a destiny node and waits the response
    public void sendGossip(GossipMessage message, NodeInfo destinationNode); // send a heartbeat without waiting for a response
     
}
