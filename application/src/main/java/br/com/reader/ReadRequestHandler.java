package br.com.reader;

import br.com.core.gossip.GossipWorker;
import br.com.core.gossip.MembershipList;
import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.Operation;
import br.com.core.model.RequestHandler;
import br.com.middleware.core.ServerRequestHandler;

public class ReadRequestHandler implements RequestHandler {

    private final DictionaryStorage dictionaryStorage;
    private final MembershipList membershipList;
    private final ServerRequestHandler serverRequestHandler;
    private GossipWorker gossipWorker;
    private NodeInfo localNode;

    public ReadRequestHandler(DictionaryStorage storage,
                              MembershipList membershipList,
                              ServerRequestHandler serverRequestHandler) {
        this.dictionaryStorage    = storage;
        this.membershipList       = membershipList;
        this.serverRequestHandler = serverRequestHandler;
    }

    public void setGossipWorker(GossipWorker gossipWorker) {
        this.gossipWorker = gossipWorker;
    }

    public void setLocalNode(NodeInfo localNode) {
        this.localNode = localNode;
    }

    @Override
    public AppResponse handleRequest(AppRequest request) {
        System.out.printf("Reader processando %s para chave '%s'%n",
            request.getOperation(), request.getKey());

        if (request.getOperation() != Operation.GET)
            return new AppResponse("405", null, "Method Not Allowed");

        java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
        if (request.getKey() != null)
            params.put("key", request.getKey());

        String result = serverRequestHandler.handle(
            "GET", "dictionary", "get", params);

        if (result.contains("\"error\""))
            return new AppResponse("500", null, result);

        if (result == null || result.equals("null"))
            return new AppResponse("200", "".getBytes(),
                "Não encontrado (aguardando gossip)");

        return new AppResponse("200",
            result.getBytes(java.nio.charset.StandardCharsets.UTF_8), "OK");
    }

    @Override
    public void handleGossip(GossipMessage gossip) {
        NodeInfo sender = gossip.getSourceNode();

        if (localNode != null) {
            if (sender.getSequenceNumber().equals(localNode.getSequenceNumber())
                    || (sender.getAddress().equals(localNode.getAddress())
                        && sender.getPort() == localNode.getPort())) return;
        }

        membershipList.updateNode(sender);
        AppRequest request = gossip.getData();

        if (request == null || request.getKey() == null) {
            System.out.printf("[Reader porta %d] Heartbeat de %s porta %d%n",
                localNode != null ? localNode.getPort() : -1,
                sender.getType(), sender.getPort());
            return;
        }

        System.out.printf("[Reader porta %d] Salvando chave '%s' via gossip%n",
            localNode != null ? localNode.getPort() : -1, request.getKey());

        switch (request.getOperation()) {
            case POST: case PUT:
                dictionaryStorage.saveLocalData(request.getKey(), request.getValue());
                break;
            case DELETE:
                dictionaryStorage.deleteLocalData(request.getKey());
                break;
            default: break;
        }

        if (gossipWorker != null && gossip.getHopCount() > 0)
            gossipWorker.spreadGossip(gossip);
    }
}