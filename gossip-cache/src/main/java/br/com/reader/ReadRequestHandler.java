package br.com.reader;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.GossipMessage;
import br.com.core.model.Operation;
import br.com.core.model.RequestHandler;

public class ReadRequestHandler implements RequestHandler{
    
    private DictionaryStorage dictionaryStorage;

    public ReadRequestHandler(DictionaryStorage storage) {
        this.dictionaryStorage = storage;
    }

    @Override
    public AppResponse handleRequest(AppRequest request) { // business rules for the reader when a request comes

        if (request.getOperation() == Operation.GET) {

            byte[] dictonaryReturn = dictionaryStorage.searchData(request.getKey());

            if (dictonaryReturn == null) {
                AppResponse response = new AppResponse("404", null, "Not Found");
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
    public void handleGossip(GossipMessage gossip) { // update the local cache silently based in the gossip network
        
        AppRequest request = gossip.getData();

        if (request.getOperation() == Operation.POST || request.getOperation() == Operation.PUT) {
            dictionaryStorage.saveLocalData(request.getKey(), request.getValue());
        } else if (request.getOperation() == Operation.DELETE) {
            dictionaryStorage.deleteLocalData(request.getKey());
        }
    }

}
