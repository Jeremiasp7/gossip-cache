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

public class ReadRequestHandler implements RequestHandler {

    private DictionaryStorage dictionaryStorage;
    private MembershipList membershipList;
    private GossipWorker gossipWorker;
    private NodeInfo localNode;

    public ReadRequestHandler(DictionaryStorage storage,
                              MembershipList membershipList) {
        this.dictionaryStorage = storage;
        this.membershipList = membershipList;
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

        if (request.getOperation() == Operation.GET) {
            byte[] value = dictionaryStorage.searchData(request.getKey());
            if (value == null) {
                return new AppResponse("200", "".getBytes(),
                        "Não encontrado (aguardando gossip)");
            }
            return new AppResponse("200", value, "OK");
        }

        return new AppResponse("405", null, "Method Not Allowed");
    }


    @Override
    public void handleGossip(GossipMessage gossip) {
        NodeInfo sender = gossip.getSourceNode();

        if (localNode != null) {
            boolean sameUUID = sender.getSequenceNumber()
                    .equals(localNode.getSequenceNumber());
            boolean sameAddress = sender.getAddress().equals(localNode.getAddress())
                    && sender.getPort() == localNode.getPort();

            if (sameUUID || sameAddress) return;
        }

        membershipList.updateNode(sender);

        AppRequest request = gossip.getData();

        if (request == null || request.getKey() == null) {
            System.out.printf(
                    "[Reader porta %d] Heartbeat recebido de %s porta %d%n",
                    localNode != null ? localNode.getPort() : -1,
                    sender.getType(), sender.getPort());
            System.out.flush();
            return;
        }

        System.out.printf(
                "[Reader porta %d] Salvando chave '%s' (valor: '%s') do gossip%n",
                localNode != null ? localNode.getPort() : -1,
                request.getKey(),
                request.getValue() != null ? new String(request.getValue()) : "null");
        System.out.flush();

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

        if (gossipWorker != null && gossip.getHopCount() > 0) {
            System.out.printf(
                    "[Reader porta %d] Propagando gossip da chave '%s' (hopCount: %d)%n",
                    localNode != null ? localNode.getPort() : -1,
                    request.getKey(), gossip.getHopCount());
            System.out.flush();
            gossipWorker.spreadGossip(gossip);
        }
    }
}