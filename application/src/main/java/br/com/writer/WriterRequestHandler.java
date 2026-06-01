package br.com.writer;

import br.com.core.gossip.GossipWorker;
import br.com.core.gossip.MembershipList;
import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;
import br.com.middleware.core.Broker;

public class WriterRequestHandler implements RequestHandler {

    private DictionaryStorage dictionaryStorage;
    private GossipWorker gossipWorker;
    private NodeInfo localNode;
    private MembershipList membershipList;
    private Broker broker;
    private static final int DATA_GOSSIP_HOP_COUNT = 1;

    public WriterRequestHandler(DictionaryStorage storage,
                                MembershipList membershipList,
                                Broker broker) {
        this.dictionaryStorage = storage;
        this.membershipList    = membershipList;
        this.broker = broker;
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

        try {
            broker.invokeCacheOperation(
                request.getOperation().toString(),
                request.getKey(),
                request.getValue());

            spreadToNetwork(request);
            return new AppResponse("200", request.getValue(), "OK");
        } catch (Exception e) {
            return new AppResponse("500", null, "Erro ao processar requisição: " + e.getMessage());
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
                break;
        }
    }
}