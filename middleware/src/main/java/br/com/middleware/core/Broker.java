package br.com.middleware.core;

import br.com.middleware.network.ProtocolPlugin;

public class Broker {

    private final Lookup lookup;
    private final Marshaller marshaller;
    private final Invoker invoker;
    private final ServerRequestHandler serverRequestHandler;
    private ProtocolPlugin protocol;

    public Broker() {
        this.lookup = new Lookup();
        this.marshaller = new Marshaller(lookup);
        this.invoker = new Invoker(lookup);
        this.serverRequestHandler = new ServerRequestHandler(invoker, marshaller);
    }

    public Broker register(Object remoteObject) {
        lookup.register(remoteObject);
        return this; // fluent API para encadear registros
    }

    public Broker useProtocol(ProtocolPlugin protocol) {
        this.protocol = protocol;
        return this;
    }

    public void start(int port) {
        if (protocol == null) {
            throw new IllegalStateException("Nenhum protocolo configurado. Chame useProtocol() antes de start().");
        }
        
        lookup.getAll().forEach((name, provider) -> {
            Object object = provider.getInstance();
            System.out.println(
                AbsoluteObjectReference.from(object, protocol.getProtocolName(), "localhost", port));
        });

        protocol.start(port, serverRequestHandler, marshaller);
    }

    public Lookup getLookup() { return lookup; }
    public Invoker getInvoker() { return invoker; }
}
