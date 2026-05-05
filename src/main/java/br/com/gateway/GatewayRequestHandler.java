package br.com.gateway;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.RequestHandler;

public class GatewayRequestHandler implements RequestHandler {

    private RequestRouter requestRouter;

    public GatewayRequestHandler(RequestRouter requestRouter) {
        this.requestRouter = requestRouter;
    }
    
    public AppResponse handleRequest(AppRequest request) { // return the request that comes in the network
        return requestRouter.routeRequest(request);
    }

    public void handleGossip(GossipMessage gossip) { // the gateway doesn't keep gossip messages
        return;
    }
}
