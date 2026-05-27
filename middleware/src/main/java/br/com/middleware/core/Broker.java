package br.com.middleware.core;

import br.com.middleware.interceptor.InterceptorChain;
import br.com.middleware.interceptor.InvocationInterceptor;
import br.com.middleware.network.ProtocolPlugin;

public class Broker {

    private final Lookup lookup;
    private final Marshaller marshaller;
    private final Invoker invoker;
    private final InterceptorChain interceptorChain;
    private final ServerRequestHandler serverRequestHandler;
    private ProtocolPlugin protocol;

    public Broker() {
        this.lookup = new Lookup();
        this.marshaller = new Marshaller(lookup);
        this.invoker = new Invoker(lookup);
        this.interceptorChain = new InterceptorChain();
        this.serverRequestHandler = new ServerRequestHandler(
            invoker, marshaller, interceptorChain);
    }

    public Broker register(Object remoteObject) {
        lookup.register(remoteObject);
        return this;
    }

    public Broker addInterceptor(InvocationInterceptor interceptor) {
        interceptorChain.add(interceptor);
        return this;
    }

    public Broker useProtocol(ProtocolPlugin protocol) {
        this.protocol = protocol;
        return this;
    }

    public ProtocolPlugin build(int port) {
        if (protocol == null)
            throw new IllegalStateException(
                "Nenhum protocolo configurado. Chame useProtocol() antes de build().");

        lookup.getAll().forEach((name, provider) -> {
            String objectName = name;
            String aor = protocol.getProtocolName() + "://localhost:"
                + port + "/" + objectName;
            System.out.println("[AOR] " + aor);
        });

        protocol.init(serverRequestHandler);
        return protocol;
    }

    public Lookup getLookup()   { return lookup; }
    public Invoker getInvoker() { return invoker; }
}