package br.com.writer;

import br.com.core.gossip.GossipWorker;
import br.com.core.gossip.MembershipList;
import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;

public class WriterRequestHandler implements RequestHandler {

    private DictionaryStorage dictionaryStorage;
    private GossipWorker gossipWorker;
    private NodeInfo localNode;
    private MembershipList membershipList;

    // -------------------------------------------------------------------------
    // hopCount para gossip de dados.
    //
    // Problema original: hopCount=2 com fan-out=3 gerava até 3 + 9 = 12
    // conexões TCP por operação de escrita.  Reduzido para 1: cada operação
    // abre no máximo 3 conexões (os 3 peers diretos).  O Gateway já faz o
    // rebroadcast explícito para todos os nós em GatewayRequestHandler, então
    // não há perda de cobertura.
    // -------------------------------------------------------------------------
    private static final int DATA_GOSSIP_HOP_COUNT = 1;

    public WriterRequestHandler(DictionaryStorage storage,
                                MembershipList membershipList) {
        this.dictionaryStorage = storage;
        this.membershipList    = membershipList;
    }

    public void setGossipWorker(GossipWorker gossipWorker) {
        this.gossipWorker = gossipWorker;
    }

    public void setLocalNode(NodeInfo localNode) {
        this.localNode = localNode;
    }

    @Override
    public AppResponse handleRequest(AppRequest request) {
        System.out.printf(
                "[Writer porta %s] Processando %s para chave '%s'%n",
                localNode != null ? localNode.getPort() : "?",
                request.getOperation(),
                request.getKey());

        switch (request.getOperation()) {
            case POST:
            case PUT:
                dictionaryStorage.saveLocalData(request.getKey(), request.getValue());
                spreadToNetwork(request);
                return new AppResponse("200", request.getValue(), "OK");

            case DELETE:
                dictionaryStorage.deleteLocalData(request.getKey());
                spreadToNetwork(request);
                return new AppResponse("200", request.getValue(), "OK");

            default:
                return new AppResponse("405", null, "Method Not Allowed");
        }
    }

    private void spreadToNetwork(AppRequest request) {
        if (gossipWorker == null || localNode == null) {
            System.out.println("[Writer] ERRO: gossipWorker ou localNode é nulo!");
            return;
        }

        System.out.printf(
                "[Writer porta %d] Propagando chave '%s' via gossip (hopCount=%d)%n",
                localNode.getPort(), request.getKey(), DATA_GOSSIP_HOP_COUNT);
        System.out.flush();

        // hopCount reduzido de 2 para 1: limita o fan-out a 3 conexões por escrita
        GossipMessage gossip = new GossipMessage(
                localNode,
                localNode.getSequenceNumber(),
                request,
                DATA_GOSSIP_HOP_COUNT);

        gossipWorker.spreadGossip(gossip);
    }

    @Override
    public void handleGossip(GossipMessage gossip) {
        NodeInfo sender = gossip.getSourceNode();
        membershipList.updateNode(sender);

        AppRequest request = gossip.getData();

        // Ignora heartbeats (chave null) — não há dado para persistir
        if (request == null || request.getKey() == null) return;

        System.out.printf("[Writer] Salvando chave '%s' recebida via gossip%n",
                request.getKey());

        switch (request.getOperation()) {
            case POST:
            case PUT:
                dictionaryStorage.saveLocalData(request.getKey(), request.getValue());
                break;
            case DELETE:
                dictionaryStorage.deleteLocalData(request.getKey());
                break;
            default:
                // GET via gossip não faz sentido — ignora silenciosamente
                break;
        }
    }
}