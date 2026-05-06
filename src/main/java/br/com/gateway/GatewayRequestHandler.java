package br.com.gateway;

import br.com.core.gossip.MembershipList;
import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;

public class GatewayRequestHandler implements RequestHandler {

    private RequestRouter requestRouter;
    private MembershipList membershipList;

    public GatewayRequestHandler(RequestRouter requestRouter) {
        this.requestRouter = requestRouter;
    }

    public void setMembershipList(MembershipList membershipList) {
        this.membershipList = membershipList;
    }
    
    public AppResponse handleRequest(AppRequest request) { // return the request that comes in the network
        return requestRouter.routeRequest(request);
    }

    public void handleGossip(GossipMessage gossip) { // the gateway doesn't keep gossip messages
        NodeInfo sender = gossip.getSourceNode();

        if (membershipList != null && sender != null) {
            membershipList.updateNode(sender);
            
            System.out.println("[Gateway] Novo nó descoberto via Gossip: " + 
                               sender.getType() + " na porta " + sender.getPort());
        }
    }
}
