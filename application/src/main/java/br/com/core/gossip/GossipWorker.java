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

    // -------------------------------------------------------------------------
    // Heartbeat separado do gossip de dados.
    //
    // Problema original: startBackgroundTest() criava um GossipMessage com
    // AppRequest de chave null e hopCount=2, abrindo até (3 peers × 2 hops)
    // conexões TCP por ciclo — apenas para sinalizar presença.  Sob carga do
    // JMeter isso esgotava as portas efêmeras (~28k) em ~25 s.
    //
    // Solução: o heartbeat agora envia apenas o NodeInfo (objeto leve) via
    // sendHeartbeat(). O gossip de *dados* (chave != null) continua funcionando
    // normalmente, mas com hopCount=1 para limitar o fan-out.
    // -------------------------------------------------------------------------

    public GossipWorker(MembershipList membershipList,
                        CommunicationStrategy communicationStrategy,
                        NodeInfo localNode,
                        ScheduledExecutorService scheduler) {
        this.membershipList        = membershipList;
        this.communicationStrategy = communicationStrategy;
        this.localNode             = localNode;
        this.scheduler             = scheduler;
    }

    // -------------------------------------------------------------------------
    // Gossip de dados — hopCount controlado pelo chamador
    // -------------------------------------------------------------------------

    /**
     * Propaga um gossip de dados para um peer específico.
     * Decrementa o hopCount antes de enviar; descarta se já for 0.
     */
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

    /**
     * Propaga um gossip de dados para até {@code fanOut} peers aleatórios.
     *
     * IMPORTANTE: só propaga se a mensagem tiver uma chave real (dados).
     * Mensagens de heartbeat (chave == null) NÃO devem usar este método —
     * use o mecanismo próprio de heartbeat em startBackgroundTask().
     */
    public void spreadGossip(GossipMessage message) {
        // Guarda defensiva: nunca propaga heartbeat nulo via gossip TCP
        if (message.getData() == null || message.getData().getKey() == null) {
            System.out.println("[GossipWorker] Mensagem sem chave ignorada em spreadGossip()");
            return;
        }

        if (message.getHopCount() <= 0) return;

        message.setHopCount(message.getHopCount() - 1);

        // Fan-out de 3 peers por hop — com hopCount=1 gera no máximo 3 conexões
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

    // -------------------------------------------------------------------------
    // Background task: heartbeat leve + remoção de nós mortos
    // -------------------------------------------------------------------------

    /**
     * Inicia dois schedulers independentes:
     *
     * 1. removeDeadNodes a cada 15 s  — limpa a membership list.
     * 2. Heartbeat a cada 5 s         — anuncia presença aos peers sem gossip
     *    de dados; usa uma mensagem GossipMessage com AppRequest de chave null
     *    e hopCount=1 (cada peer recebe 1 mensagem, não propaga).
     *
     * A separação evita que a falha de envio de heartbeat bloqueie a remoção
     * de nós mortos, e vice-versa.
     */
    public void startBackgroundTest() {

        // --- tarefa 1: limpeza de nós mortos ---
        scheduler.scheduleAtFixedRate(() -> {
            try {
                membershipList.removeDeadNodes();
            } catch (Exception e) {
                System.err.println("[GossipWorker] Erro em removeDeadNodes: "
                        + e.getMessage());
            }
        }, 15, 15, TimeUnit.SECONDS);

        // --- tarefa 2: heartbeat leve ---
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

    /**
     * Envia um heartbeat leve (AppRequest com chave null, hopCount=1) para
     * até 3 peers aleatórios.  O receptor atualiza o lastHeartbeat do nó
     * sem propagar a mensagem adiante (hopCount chegará a 0 no destinatário).
     *
     * Usa o mesmo canal TCP/UDP do gossip de dados para não abrir porta extra,
     * mas com hopCount=1 garante que NÃO há fan-out secundário — cada ciclo
     * abre no máximo 3 conexões.
     */
    private void sendHeartbeat() {
        // hopCount = 1: o destinatário recebe mas NÃO repropaga
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
                    // Falha de heartbeat é silenciosa — o nó será removido
                    // naturalmente pelo removeDeadNodes() se ficar offline
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