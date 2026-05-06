package br.com.core.gossip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import br.com.core.model.NodeInfo;

public class MembershipList {
    
    private NodeInfo localNode; // the node have to known who he is
    private ConcurrentHashMap<UUID, NodeInfo> activeNodes; // routing table
    private CopyOnWriteArrayList<NodeInfo> readerNodes; // readers list
    private CopyOnWriteArrayList<NodeInfo> writerNodes; // writers list
    private CopyOnWriteArrayList<NodeInfo> gatewayNodes; //gateways list
    
    public MembershipList(NodeInfo localNode) {
        this.localNode = localNode;
        this.activeNodes = new ConcurrentHashMap<>();
        this.readerNodes = new CopyOnWriteArrayList<>();
        this.writerNodes = new CopyOnWriteArrayList<>();
        this.gatewayNodes = new CopyOnWriteArrayList<>();
        this.localNode.setLastHeartbeat(System.currentTimeMillis());
        this.activeNodes.put(localNode.getSequenceNumber(), localNode);
        addNodeToTypedList(localNode);
    }
    
    public void updateNode(NodeInfo incomingNode) { // updating the node last heartbeat or putting him on the list

        if (incomingNode.getSequenceNumber().equals(localNode.getSequenceNumber())) {
            return;
        }

        incomingNode.setLastHeartbeat(System.currentTimeMillis());
        
        activeNodes.put(incomingNode.getSequenceNumber(), incomingNode);
        addNodeToTypedList(incomingNode);
    }

    private void addNodeToTypedList(NodeInfo node) {
        removeNodeFromTypedList(node);
        switch (node.getType()) {
            case READER:
                readerNodes.add(node);
                break;
            case WRITER:
                writerNodes.add(node);
                break;
            case GATEWAY:
                gatewayNodes.add(node);
                break;
        }
    }

    private void removeNodeFromTypedList(NodeInfo node) {
        readerNodes.removeIf(n -> n.getSequenceNumber().equals(node.getSequenceNumber()));
        writerNodes.removeIf(n -> n.getSequenceNumber().equals(node.getSequenceNumber()));
        gatewayNodes.removeIf(n -> n.getSequenceNumber().equals(node.getSequenceNumber()));
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
    
    public List<NodeInfo> getReaderNodes() {
        return new ArrayList<>(readerNodes);
    }

    public List<NodeInfo> getWriterNodes() {
        return new ArrayList<>(writerNodes);
    }

    public List<NodeInfo> getGatewayNodes() {
        return new ArrayList<>(gatewayNodes);
    }
}
