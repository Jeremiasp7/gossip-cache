package br.com.writer;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.Operation;
import br.com.core.model.RequestHandler;

public class WriteRequestHandler implements RequestHandler {

    private DictionaryStorage dictionaryStorage;

    public WriteRequestHandler(DictionaryStorage storage) {
        this.dictionaryStorage = storage;
    }
    
    @Override
    public AppResponse handleRequest(AppRequest request) { // business rules for the writer when a request comes

        if(request.getOperation() == Operation.POST || request.getOperation() == Operation.PUT) {

            dictionaryStorage.saveLocalData(request.getKey(), request.getValue());
            
            AppResponse response = new AppResponse("200", request.getValue(), "OK");

            return response;
        } else if (request.getOperation() == Operation.DELETE) {

            dictionaryStorage.deleteLocalData(request.getKey());
            
            AppResponse response = new AppResponse("200", request.getValue(), "OK");

            return response;
        } else {

            AppResponse response = new AppResponse("405", null, "Method Not Allowed");
            return response;
        }
    }
    
}
