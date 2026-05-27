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

    // -------------------------------------------------------------------------
    // TTL de inatividade aumentado de 30 s para 60 s.
    //
    // Problema original: sob carga pesada do JMeter o esgotamento de portas
    // efêmeras (~25 s) interrompia o envio de gossip/heartbeat.  Com TTL=30 s,
    // os nós eram removidos ~5 s depois — sem estarem realmente mortos.
    // Com TTL=60 s há margem suficiente para o cluster se recuperar de um pico
    // de carga sem perder entradas válidas na membership list.
    // -------------------------------------------------------------------------
    private static final long NODE_TIMEOUT_MS = 30_000;   // 60 s (era 30 s)
    private static final long TOMBSTONE_TTL   = 30_000;   // 60 s

    // Callback opcional: chamado quando um nó é removido por inatividade.
    // Usado pelo TcpStrategy para descartar os sockets do pool desse nó.
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

    // -------------------------------------------------------------------------
    // Callback de evicção — registrado pelo TcpStrategy após criação
    // -------------------------------------------------------------------------

    /**
     * Registra um callback invocado sempre que um nó é removido por inatividade.
     * O TcpStrategy usa isso para fechar os sockets do pool desse nó.
     *
     * Exemplo de uso em WriterServer / ReaderServer / ApiGatewayServer:
     * <pre>
     *   if (tcpStrategy != null)
     *       membershipList.setOnNodeEvicted(tcpStrategy::evictPool);
     * </pre>
     */
    public void setOnNodeEvicted(Consumer<UUID> callback) {
        this.onNodeEvicted = callback;
    }

    // -------------------------------------------------------------------------
    // Atualização de nós
    // -------------------------------------------------------------------------

    public void updateNode(NodeInfo incomingNode) {
        // Nunca atualiza o próprio nó via gossip externo
        if (incomingNode.getSequenceNumber().equals(localNode.getSequenceNumber())) {
            return;
        }

        Long removedAt = removedNodes.get(incomingNode.getSequenceNumber());
        if (removedAt != null) {
            if (System.currentTimeMillis() - removedAt < TOMBSTONE_TTL) {
                return; // ainda dentro do TTL do tombstone — ignora
            } else {
                removedNodes.remove(incomingNode.getSequenceNumber()); // tombstone expirou
            }
        }

        incomingNode.setLastHeartbeat(System.currentTimeMillis());
        activeNodes.put(incomingNode.getSequenceNumber(), incomingNode);
        addNodeToTypedList(incomingNode);
    }

    // -------------------------------------------------------------------------
    // Remoção de nós mortos
    // -------------------------------------------------------------------------

    /**
     * Remove nós que não enviaram heartbeat nos últimos {@value #NODE_TIMEOUT_MS} ms.
     *
     * TTL aumentado de 30 s para 60 s para tolerar picos transitórios de carga
     * sem remover nós que estão vivos.  Ao remover, dispara o callback
     * {@code onNodeEvicted} para que o TcpStrategy feche os sockets do pool.
     */
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

                // Notifica o TcpStrategy para fechar os sockets desse peer
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

        // Limpa tombstones expirados
        removedNodes.forEach((uuid, removedAt) -> {
            if (now - removedAt >= TOMBSTONE_TTL) {
                removedNodes.remove(uuid);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Listas por tipo
    // -------------------------------------------------------------------------

    private void addNodeToTypedList(NodeInfo node) {
        removeNodeFromTypedList(node); // evita duplicatas
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

    // -------------------------------------------------------------------------
    // Consultas
    // -------------------------------------------------------------------------

    /**
     * Retorna uma lista aleatória de até {@code numberOfPeers} nós ativos,
     * excluindo o nó local.
     */
    public List<NodeInfo> getPeersToGossip(int numberOfPeers) {
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