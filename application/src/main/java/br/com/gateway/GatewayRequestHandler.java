package br.com.gateway;

import br.com.core.gossip.GossipWorker;
import br.com.core.gossip.MembershipList;
import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;

public class GatewayRequestHandler implements RequestHandler {

    private RequestRouter requestRouter;
    private MembershipList membershipList;
    private GossipWorker gossipWorker;

    public GatewayRequestHandler(RequestRouter requestRouter) {
        this.requestRouter = requestRouter;
    }

    public void setMembershipList(MembershipList membershipList) {
        this.membershipList = membershipList;
    }

    public void setGossipWorker(GossipWorker gossipWorker) {
        this.gossipWorker = gossipWorker;
    }

    @Override
    public AppResponse handleRequest(AppRequest request) { // return the request that comes in the network
        return requestRouter.routeRequest(request);
    }

    @Override
    public void handleGossip(GossipMessage gossip) {
        NodeInfo sender = gossip.getSourceNode();

        if (membershipList == null || sender == null) return;

        membershipList.updateNode(sender);

        AppRequest data = gossip.getData();
        String key = (data != null && data.getKey() != null)
            ? data.getKey() : "null";

        System.out.println("[Gateway] Heartbeat de " + sender.getType()
            + " na porta " + sender.getPort() + " (chave: " + key + ")");
        System.out.flush();

        // Rebroadcast APENAS para gossip de dados — não para heartbeats (key == null)
        if (gossipWorker != null && gossip.getHopCount() > 0
                && data != null && data.getKey() != null) {
            System.out.println("[Gateway] Repassando gossip da chave '"
                + key + "' para todos os nós");
            for (NodeInfo peer : membershipList.getNodes().values()) {
                if (!peer.getSequenceNumber().equals(sender.getSequenceNumber())) {
                    gossipWorker.spreadGossipToPeer(gossip, peer);
                }
            }
        }
    }
}
