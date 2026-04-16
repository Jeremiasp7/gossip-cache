package br.com.reader;

import br.com.core.gossip.MembershipList;
import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.Operation;
import br.com.core.model.RequestHandler;

public class ReadRequestHandler implements RequestHandler{
    
    private DictionaryStorage dictionaryStorage;
    private MembershipList membershipList;

    public ReadRequestHandler(DictionaryStorage storage, MembershipList membershipList) {
        this.dictionaryStorage = storage;
        this.membershipList = membershipList;
    }

    @Override
    public AppResponse handleRequest(AppRequest request) { 

        System.out.println("Reader processando requisição " + request.getOperation() + " para a chave: '" + request.getKey() + "'");

        if (request.getOperation() == Operation.GET) {

            byte[] dictonaryReturn = dictionaryStorage.searchData(request.getKey());

            if (dictonaryReturn == null) {
                AppResponse response = new AppResponse("200", "".getBytes(), "Nao encontrado (Aguardando fofoca)");
                return response;
            } else {
                AppResponse response = new AppResponse("200", dictonaryReturn, "OK");
                return response;
            }
        } else {
            AppResponse response = new AppResponse("405", null, "Method Not Allowed");
            return response;
        }
    }

    @Override
    public void handleGossip(GossipMessage gossip) { 

        NodeInfo sender = gossip.getSourceNode(); 
        membershipList.updateNode(sender);
        
        AppRequest request = gossip.getData();
        
        System.out.println("O Reader acaba de salvar a chave: '" + request.getKey() + "'!");

        if (request != null && request.getKey() != null) {
            System.out.println("O Writer acaba de salvar a chave: '" + request.getKey() + "'!");
            if (request.getOperation() == Operation.POST || request.getOperation() == Operation.PUT) {
                dictionaryStorage.saveLocalData(request.getKey(), request.getValue());
            } else if (request.getOperation() == Operation.DELETE) {
                dictionaryStorage.deleteLocalData(request.getKey());
            }
        }
    }
}