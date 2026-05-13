package br.com.core.gossip;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import br.com.core.model.AppRequest;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.Operation;
import br.com.core.network.CommunicationStrategy;

public class GossipWorker {
    
    private MembershipList membershipList; 
    private CommunicationStrategy communicationStrategy; 
    private NodeInfo localNode; 
    private ScheduledExecutorService scheduler; 

    public GossipWorker(MembershipList membershipList, CommunicationStrategy communicationStrategy, NodeInfo localNode,
            ScheduledExecutorService scheduler) {
        this.membershipList = membershipList;
        this.communicationStrategy = communicationStrategy;
        this.localNode = localNode;
        this.scheduler = scheduler;
    }

    public void spreadGossipToPeer(GossipMessage message, NodeInfo peer) {
        if (message.getHopCount() <= 0) {
            return;
        }

        // Create a copy to avoid modifying original
        GossipMessage copy = new GossipMessage(message.getSourceNode(), message.getSequenceNumber(), message.getData(), message.getHopCount() - 1);
        System.out.println("[" + localNode.getType() + " na porta " + localNode.getPort() + "] Enviando gossip para " + peer.getType() + " na porta " + peer.getPort());
        communicationStrategy.sendGossip(copy, peer);
    }

    public void spreadGossip(GossipMessage message) {
        if (message.getHopCount() <= 0) {
            return;
        }

        message.setHopCount(message.getHopCount() - 1);

        List<NodeInfo> list = membershipList.getPeersToGossip(3);

        if (list.isEmpty()) {
            System.out.println("[" + localNode.getType() + " na porta " + localNode.getPort() + "] Nenhum peer para enviar gossip");
            return;
        }

        for (NodeInfo nodeInfo : list) {
            if (!nodeInfo.getSequenceNumber().equals(localNode.getSequenceNumber())) {
                String key = (message.getData() != null && message.getData().getKey() != null) ? message.getData().getKey() : "null";
                System.out.println("[" + localNode.getType() + " na porta " + localNode.getPort() + "] Enviando gossip para " + nodeInfo.getType() + " na porta " + nodeInfo.getPort() + " (chave: " + key + ")");
                System.out.flush();
                communicationStrategy.sendGossip(message, nodeInfo);
            }
        }
    }

    public void startBackgroundTest() {

        scheduler.scheduleAtFixedRate(() -> {
            try {
                membershipList.removeDeadNodes();
                localNode.setLastHeartbeat(System.currentTimeMillis());

                AppRequest request = new AppRequest(Operation.GET, null, null);
                GossipMessage gossip = new GossipMessage(localNode, localNode.getSequenceNumber(), request, 2);
                spreadGossip(gossip);

            } catch(Exception e) {
                e.printStackTrace();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    public void stop() { 
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
