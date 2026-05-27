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

    private final MembershipList membershipList;
    private final CommunicationStrategy communicationStrategy;
    private final NodeInfo localNode;
    private final ScheduledExecutorService scheduler;


    public GossipWorker(MembershipList membershipList,
                        CommunicationStrategy communicationStrategy,
                        NodeInfo localNode,
                        ScheduledExecutorService scheduler) {
        this.membershipList        = membershipList;
        this.communicationStrategy = communicationStrategy;
        this.localNode             = localNode;
        this.scheduler             = scheduler;
    }


    public void spreadGossipToPeer(GossipMessage message, NodeInfo peer) {
        if (message.getHopCount() <= 0) return;

        GossipMessage copy = new GossipMessage(
                message.getSourceNode(),
                message.getSequenceNumber(),
                message.getData(),
                message.getHopCount() - 1);

        System.out.printf("[%s porta %d] Enviando gossip para %s porta %d%n",
                localNode.getType(), localNode.getPort(),
                peer.getType(), peer.getPort());

        communicationStrategy.sendGossip(copy, peer);
    }


    public void spreadGossip(GossipMessage message) { // just propagate the gossip when the key is not null
        if (message.getData() == null || message.getData().getKey() == null) {
            System.out.println("[GossipWorker] Mensagem sem chave ignorada em spreadGossip()");
            return;
        }

        if (message.getHopCount() <= 0) return;

        message.setHopCount(message.getHopCount() - 1);

        List<NodeInfo> peers = membershipList.getPeersToGossip(3);

        if (peers.isEmpty()) {
            System.out.printf("[%s porta %d] Nenhum peer para enviar gossip%n",
                    localNode.getType(), localNode.getPort());
            return;
        }

        String key = message.getData().getKey();
        for (NodeInfo peer : peers) {
            if (!peer.getSequenceNumber().equals(localNode.getSequenceNumber())) {
                System.out.printf(
                        "[%s porta %d] Enviando gossip para %s porta %d (chave: %s)%n",
                        localNode.getType(), localNode.getPort(),
                        peer.getType(), peer.getPort(), key);
                System.out.flush();
                communicationStrategy.sendGossip(message, peer);
            }
        }
    }

    public void startBackgroundTest() { // remove dead nodes and announces the live nodes

        scheduler.scheduleAtFixedRate(() -> {
            try {
                membershipList.removeDeadNodes();
            } catch (Exception e) {
                System.err.println("[GossipWorker] Erro em removeDeadNodes: "
                        + e.getMessage());
            }
        }, 15, 15, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                localNode.setLastHeartbeat(System.currentTimeMillis());
                sendHeartbeat();
            } catch (Exception e) {
                System.err.println("[GossipWorker] Erro no heartbeat: "
                        + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        AppRequest heartbeatPayload = new AppRequest(Operation.GET, null, null);
        GossipMessage heartbeat = new GossipMessage(
                localNode,
                localNode.getSequenceNumber(),
                heartbeatPayload,
                1);

        List<NodeInfo> peers = membershipList.getPeersToGossip(3);
        for (NodeInfo peer : peers) {
            if (!peer.getSequenceNumber().equals(localNode.getSequenceNumber())) {
                try {
                    communicationStrategy.sendGossip(heartbeat, peer);
                } catch (Exception e) {
                    System.err.println("[GossipWorker] Heartbeat falhou para porta "
                            + peer.getPort() + ": " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}