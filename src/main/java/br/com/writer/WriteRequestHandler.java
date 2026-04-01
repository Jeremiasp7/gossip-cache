package main.java.br.com.writer;

import model.AppRequest;
import model.AppResponse;
import model.Operation;
import model.RequestHandler;
import model.DictionaryStorage;

public class WriteRequestHandler implements RequestHandler {

    private DictionaryStorage dictionaryStorage;

    public WriteRequestHandler(DictionaryStorage storage) {
        this.dictionaryStorage = storage;
    }
    
    @Override
    public AppResponse handleRequest(AppRequest request) {

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
