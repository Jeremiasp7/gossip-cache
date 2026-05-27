package br.com.middleware.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final Map<String, AbsoluteObjectReference> aorRegistry = new LinkedHashMap<>();

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

        String host = resolveHost();
        lookup.getAll().forEach((name, provider) -> {
            AbsoluteObjectReference aor = new AbsoluteObjectReference(
                protocol.getProtocolName(), host, port, name);
            aorRegistry.put(name, aor);
            System.out.println(aor);
        });

        protocol.init(serverRequestHandler);
        return protocol;
    }

    public AbsoluteObjectReference getAor(String objectName) {
        AbsoluteObjectReference aor = aorRegistry.get(objectName);
        if (aor == null)
            throw new RuntimeException("[Broker] AOR não encontrado para: " + objectName);
        return aor;
    }

    public Map<String, AbsoluteObjectReference> getAllAors() {
        return Collections.unmodifiableMap(aorRegistry);
    }

    private String resolveHost() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    public Lookup getLookup()   { return lookup; }
    public Invoker getInvoker() { return invoker; }
}