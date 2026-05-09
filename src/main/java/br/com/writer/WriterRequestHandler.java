package br.com.writer;

import br.com.core.gossip.GossipWorker;
import br.com.core.gossip.MembershipList;
import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.Operation;
import br.com.core.model.RequestHandler;

public class WriterRequestHandler implements RequestHandler {

    private DictionaryStorage dictionaryStorage;
    private GossipWorker gossipWorker;
    private NodeInfo localNode; // maybe a problem explodes here
    private MembershipList membershipList;

    public WriterRequestHandler(DictionaryStorage storage, MembershipList membershipList) {
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

        System.out.println("[Writer na porta " + (localNode != null ? localNode.getPort() : "?") + "] Processando requisição " + request.getOperation() + " para a chave: '" + request.getKey() + "'");

        if(request.getOperation() == Operation.POST || request.getOperation() == Operation.PUT) {

            dictionaryStorage.saveLocalData(request.getKey(), request.getValue());
            
            spreadToNetwork(request);
            
            return new AppResponse("200", request.getValue(), "OK");

        } else if (request.getOperation() == Operation.DELETE) {

            dictionaryStorage.deleteLocalData(request.getKey());
            
            spreadToNetwork(request);
            
            return new AppResponse("200", request.getValue(), "OK");

        } else {
            return new AppResponse("405", null, "Method Not Allowed");
        }
    }

    private void spreadToNetwork(AppRequest request) {
        if (gossipWorker != null && localNode != null) {
            System.out.println("[Writer na porta " + localNode.getPort() + "] Propagando chave '" + request.getKey() + "' para a rede via gossip");
            System.out.flush();
            GossipMessage gossip = new GossipMessage(localNode, localNode.getSequenceNumber(), request, 2);
            gossipWorker.spreadGossip(gossip);
        } else {
            System.out.println("[Writer] ERRO: gossipWorker ou localNode é nulo!");
        }
    }

    @Override
    public void handleGossip(GossipMessage gossip) { 

        NodeInfo sender = gossip.getSourceNode(); 
        membershipList.updateNode(sender);

        AppRequest request = gossip.getData();

        if (request != null && request.getKey() != null) {
            System.out.println("O Reader acaba de salvar a chave: '" + request.getKey() + "'!");
            if (request.getOperation() == Operation.POST || request.getOperation() == Operation.PUT) {
                dictionaryStorage.saveLocalData(request.getKey(), request.getValue());
            } else if (request.getOperation() == Operation.DELETE) {
                dictionaryStorage.deleteLocalData(request.getKey());
            }
        }
    }
}