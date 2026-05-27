package br.com.core.gossip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import br.com.core.model.NodeInfo;

public class MembershipList {

    private final NodeInfo localNode;
    private final ConcurrentHashMap<UUID, NodeInfo> activeNodes;
    private final CopyOnWriteArrayList<NodeInfo> readerNodes;
    private final CopyOnWriteArrayList<NodeInfo> writerNodes;
    private final CopyOnWriteArrayList<NodeInfo> gatewayNodes;
    private final ConcurrentHashMap<UUID, Long> removedNodes = new ConcurrentHashMap<>();
    private static final long NODE_TIMEOUT_MS = 30_000;
    private static final long TOMBSTONE_TTL   = 30_000;

    private Consumer<UUID> onNodeEvicted; 

    public MembershipList(NodeInfo localNode) {
        this.localNode    = localNode;
        this.activeNodes  = new ConcurrentHashMap<>();
        this.readerNodes  = new CopyOnWriteArrayList<>();
        this.writerNodes  = new CopyOnWriteArrayList<>();
        this.gatewayNodes = new CopyOnWriteArrayList<>();
        this.localNode.setLastHeartbeat(System.currentTimeMillis());
        this.activeNodes.put(localNode.getSequenceNumber(), localNode);
        addNodeToTypedList(localNode);
    }


    public void setOnNodeEvicted(Consumer<UUID> callback) {
        this.onNodeEvicted = callback;
    }


    public void updateNode(NodeInfo incomingNode) {
        if (incomingNode.getSequenceNumber().equals(localNode.getSequenceNumber())) {
            return;
        }

        Long removedAt = removedNodes.get(incomingNode.getSequenceNumber());
        if (removedAt != null) {
            if (System.currentTimeMillis() - removedAt < TOMBSTONE_TTL) {
                return;
            } else {
                removedNodes.remove(incomingNode.getSequenceNumber());
            }
        }

        incomingNode.setLastHeartbeat(System.currentTimeMillis());
        activeNodes.put(incomingNode.getSequenceNumber(), incomingNode);
        addNodeToTypedList(incomingNode);
    }


    public void removeDeadNodes() {
        long now = System.currentTimeMillis();

        activeNodes.forEach((id, node) -> {
            if (node.getSequenceNumber().equals(localNode.getSequenceNumber())) return;

            if (now - node.getLastHeartbeat() > NODE_TIMEOUT_MS) {
                activeNodes.remove(id);
                removeNodeFromTypedList(node);
                removedNodes.put(node.getSequenceNumber(), now);
                System.out.printf("Nó porta %d removido por inatividade (sem heartbeat há %d s).%n",
                        node.getPort(), (now - node.getLastHeartbeat()) / 1000);

                if (onNodeEvicted != null) {
                    try {
                        onNodeEvicted.accept(id);
                    } catch (Exception e) {
                        System.err.println("[MembershipList] Erro no callback de evicção: "
                                + e.getMessage());
                    }
                }
            }
        });

        removedNodes.forEach((uuid, removedAt) -> {
            if (now - removedAt >= TOMBSTONE_TTL) {
                removedNodes.remove(uuid);
            }
        });
    }


    private void addNodeToTypedList(NodeInfo node) {
        removeNodeFromTypedList(node);
        switch (node.getType()) {
            case READER:  readerNodes.add(node);  break;
            case WRITER:  writerNodes.add(node);  break;
            case GATEWAY: gatewayNodes.add(node); break;
        }
    }

    private void removeNodeFromTypedList(NodeInfo node) {
        readerNodes .removeIf(n -> n.getSequenceNumber().equals(node.getSequenceNumber()));
        writerNodes .removeIf(n -> n.getSequenceNumber().equals(node.getSequenceNumber()));
        gatewayNodes.removeIf(n -> n.getSequenceNumber().equals(node.getSequenceNumber()));
    }


    public List<NodeInfo> getPeersToGossip(int numberOfPeers) { // return a list of active nodes
        List<NodeInfo> list = new ArrayList<>(activeNodes.values());
        list.removeIf(n -> n.getSequenceNumber().equals(localNode.getSequenceNumber()));
        Collections.shuffle(list);
        return list.size() <= numberOfPeers
                ? list
                : new ArrayList<>(list.subList(0, numberOfPeers));
    }

    public ConcurrentHashMap<UUID, NodeInfo> getNodes() { return activeNodes; }

    public List<NodeInfo> getReaderNodes()  { return new ArrayList<>(readerNodes);  }
    public List<NodeInfo> getWriterNodes()  { return new ArrayList<>(writerNodes);  }
    public List<NodeInfo> getGatewayNodes() { return new ArrayList<>(gatewayNodes); }
}