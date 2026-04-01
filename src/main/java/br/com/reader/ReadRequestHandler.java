package main.java.br.com.reader;

import model.AppRequest;
import model.AppResponse;
import model.DictionaryStorage;
import model.Operation;
import model.RequestHandler;

public class ReadRequestHandler implements RequestHandler{
    
    private DictionaryStorage dictionaryStorage;

    public ReadRequestHandler(DictionaryStorage storage) {
        this.dictionaryStorage = storage;
    }

    @Override
    public AppResponse handleRequest(AppRequest request) {

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

}
