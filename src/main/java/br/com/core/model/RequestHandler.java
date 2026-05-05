package br.com.core.model;

public interface RequestHandler {
    
    public AppResponse handleRequest(AppRequest request); // process the request through you own rules

    public void handleGossip(GossipMessage gossip); // process the incoming gossip in the background
    
}
