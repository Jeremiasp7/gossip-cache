package br.com.writer;

import br.com.core.gossip.GossipWorker;
import br.com.core.gossip.MembershipList;
import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;
import br.com.middleware.core.ServerRequestHandler;

public class WriterRequestHandler implements RequestHandler {

    private final DictionaryStorage dictionaryStorage;
    private final MembershipList membershipList;
    private final ServerRequestHandler serverRequestHandler;
    private GossipWorker gossipWorker;
    private NodeInfo localNode;
    private static final int DATA_GOSSIP_HOP_COUNT = 1;

    public WriterRequestHandler(DictionaryStorage storage,
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
        System.out.printf("[Writer porta %s] Processando %s para chave '%s'%n",
            localNode != null ? localNode.getPort() : "?",
            request.getOperation(), request.getKey());

        String httpMethod = operationToHttpMethod(request.getOperation().name());
        String methodPath = operationToPath(request.getOperation().name());

        java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
        if (request.getKey() != null)
            params.put("key", request.getKey());
        if (request.getValue() != null)
            params.put("value", new String(
                request.getValue(), java.nio.charset.StandardCharsets.UTF_8));

        String result = serverRequestHandler.handle(
            httpMethod, "dictionary", methodPath, params);

        if (result.contains("\"error\""))
            return new AppResponse("500", null, result);

        spreadToNetwork(request);
        return new AppResponse("200", request.getValue(), "OK");
    }

    @Override
    public void handleGossip(GossipMessage gossip) {
        NodeInfo sender = gossip.getSourceNode();
        membershipList.updateNode(sender);

        AppRequest request = gossip.getData();
        if (request == null || request.getKey() == null) return;

        System.out.printf("[Writer] Salvando chave '%s' via gossip%n",
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

    private void spreadToNetwork(AppRequest request) {
        if (gossipWorker == null || localNode == null) {
            System.out.println("[Writer] ERRO: gossipWorker ou localNode é nulo!");
            return;
        }
        System.out.printf("[Writer porta %d] Propagando chave '%s' via gossip%n",
            localNode.getPort(), request.getKey());
        GossipMessage gossip = new GossipMessage(
            localNode, localNode.getSequenceNumber(), request, DATA_GOSSIP_HOP_COUNT);
        gossipWorker.spreadGossip(gossip);
    }

    private String operationToPath(String operation) {
        switch (operation.toUpperCase()) {
            case "GET":           return "get";
            case "POST": case "PUT": return "post";
            case "DELETE":        return "delete";
            default: throw new RuntimeException("Operação não mapeada: " + operation);
        }
    }

    private String operationToHttpMethod(String operation) {
        return operation.equalsIgnoreCase("GET") ? "GET" : "POST";
    }
}