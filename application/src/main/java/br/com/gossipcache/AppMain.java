package br.com.gossipcache;

import br.com.core.model.DictionaryStorage;
import br.com.middleware.core.AbsoluteObjectReference;
import br.com.middleware.core.Invoker;
import br.com.middleware.core.Lookup;
import br.com.middleware.core.Marshaller;
import br.com.middleware.core.ServerRequestHandler;
import br.com.middleware.network.HttpPlugin;

public class AppMain {
    public static void main(String[] args) throws Exception {
        int port = 8080;

        Lookup lookup               = new Lookup();
        lookup.register(new DictionaryStorage());

        Marshaller marshaller       = new Marshaller(lookup);
        Invoker invoker             = new Invoker(lookup);
        ServerRequestHandler srh    = new ServerRequestHandler(invoker);

        // Logar AORs
        lookup.getAll().forEach((name, obj) -> System.out.println(
            AbsoluteObjectReference.from(obj, "http", "localhost", port)));

        new HttpPlugin(port, lookup, marshaller, srh).start();
    }
}