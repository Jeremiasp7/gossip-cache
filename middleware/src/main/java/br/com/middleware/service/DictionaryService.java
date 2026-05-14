package br.com.middleware.service;

import br.com.middleware.annotations.RemoteObject;
import br.com.middleware.annotations.MethodMapping;
import br.com.middleware.annotations.MethodHTTP;

@RemoteObject(name = "DictionaryService")
public class DictionaryService {

    @MethodMapping(method = MethodHTTP.GET, path = "/dictionary/get")
    public String get(String key) {
        return "value_for_" + key;
    }

    @MethodMapping(method = MethodHTTP.POST, path = "/dictionary/save")
    public void saveLocalData(String key, String value) {
        // escreve na memória local e lida com gossip
    }
}
