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
    public void handleGossip(GossipMessage gossip) { // the gateway distributes gossip to all nodes
        NodeInfo sender = gossip.getSourceNode();

        if (membershipList != null && sender != null) {
            membershipList.updateNode(sender);

            AppRequest data = gossip.getData();
            String key = (data != null && data.getKey() != null) ? data.getKey() : "null";
            System.out.println("[Gateway] Novo nó descoberto via Gossip: " +
                               sender.getType() + " na porta " + sender.getPort() + " (chave: " + key + ")");
            System.out.flush();

            // Repassar gossip para TODOS os peers (exceto a origem)
            if (gossipWorker != null && gossip.getHopCount() > 0) {
                System.out.println("[Gateway] Repassando gossip da chave '" + key + "' para TODOS os nós");
                System.out.flush();

                for (NodeInfo peer : membershipList.getNodes().values()) {
                    if (!peer.getSequenceNumber().equals(sender.getSequenceNumber())) {
                        System.out.println("[Gateway-Rebroadcast] Enviando para " + peer.getType() + " na porta " + peer.getPort());
                        gossipWorker.spreadGossipToPeer(gossip, peer);
                    }
                }
            }
        }
    }
}
