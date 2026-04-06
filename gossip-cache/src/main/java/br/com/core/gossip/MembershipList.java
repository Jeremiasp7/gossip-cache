package br.com.core.gossip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import br.com.core.model.NodeInfo;

public class MembershipList {
    
    private NodeInfo localNode; // the node have to known who he is
    private ConcurrentHashMap<UUID, NodeInfo> activeNodes; // routing table

    public MembershipList() {}
    
    public MembershipList(NodeInfo localNode, ConcurrentHashMap<UUID, NodeInfo> activeNodes) {
        this.localNode = localNode;
        this.activeNodes = new ConcurrentHashMap<>();

        this.localNode.setLastHeartbeat(System.currentTimeMillis());
        this.activeNodes.put(localNode.getSequenceNumber(), localNode);
    }
    
    public void updateNode(NodeInfo incomingNode) { // updating the node last heartbeat or putting him on the list

        if (incomingNode.getSequenceNumber().equals(localNode.getSequenceNumber())) {
            return;
        }

        incomingNode.setLastHeartbeat(System.currentTimeMillis());
        
        activeNodes.put(incomingNode.getSequenceNumber(), incomingNode);
    }
    
    public List<NodeInfo> getPeersToGossip(int numberOfPeers) { // getting a sublist of the nodes with the size of the number of peers

        List<NodeInfo> nodesList = new ArrayList<>(activeNodes.values());

        nodesList.removeIf(node -> node.getSequenceNumber().equals(localNode.getSequenceNumber()));
        Collections.shuffle(nodesList);

        if (nodesList.size() < numberOfPeers) {
            return nodesList;
        } else {
            return new ArrayList<>(nodesList.subList(0, numberOfPeers));
        }
    }


    public void removeDeadNodes() { // remove a node if hist last heartbeat it happend more than 10 seconds ago

        long tempoAtual = System.currentTimeMillis();

        activeNodes.forEach((id, node) -> {
            if (tempoAtual - node.getLastHeartbeat() > 10000) {
                if (!node.getSequenceNumber().equals(localNode.getSequenceNumber())) {
                    activeNodes.remove(id);
                    System.out.println("Nó " + node.getPort() + " foi removido por inatividade.");
                }
            }
        });
    }

    public ConcurrentHashMap<UUID, NodeInfo> getNodes() { // return the active nodes list
        return activeNodes;
    }
    
}
