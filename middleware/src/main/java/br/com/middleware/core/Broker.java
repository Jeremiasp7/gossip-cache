package br.com.middleware.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.middleware.interceptor.InterceptorChain;
import br.com.middleware.interceptor.InvocationInterceptor;
import br.com.middleware.lifecycle.InstanceFactory;
import br.com.middleware.network.MiddlewareServer;
import br.com.middleware.network.ProtocolPlugin;
import br.com.middleware.dto.InvocationReply;
import br.com.middleware.dto.InvocationRequest;

public class Broker {

    private final Lookup lookup;
    private final Marshaller marshaller;
    private final Invoker invoker;
    private final InterceptorChain interceptorChain;
    private final ServerRequestHandler serverRequestHandler;
    private ProtocolPlugin protocol;
    private final Map<String, AbsoluteObjectReference> aorRegistry = new LinkedHashMap<>();

    public Broker(Lookup lookup, Marshaller marshaller, Invoker invoker,
                  InterceptorChain interceptorChain, ServerRequestHandler serverRequestHandler) {
        this.lookup = lookup;
        this.marshaller = marshaller;
        this.invoker = invoker;
        this.interceptorChain = interceptorChain;
        this.serverRequestHandler = serverRequestHandler;
    }

    /**
     * Factory method que cria um Broker com configuração padrão.
     * Útil para bootstrap sem necessidade de DI container.
     */
    public static Broker createDefault() {
        Lookup lookup = new Lookup();
        Marshaller marshaller = new Marshaller(lookup);
        Invoker invoker = new Invoker(lookup);
        InterceptorChain interceptorChain = new InterceptorChain();
        ServerRequestHandler serverRequestHandler = new ServerRequestHandler(
            invoker, marshaller, interceptorChain);
        return new Broker(lookup, marshaller, invoker, interceptorChain, serverRequestHandler);
    }

    public Broker register(Object remoteObject) {
        lookup.register(remoteObject);
        return this;
    }

    public Broker register(Object remoteObject, InstanceFactory factory) {
        lookup.register(remoteObject, factory);
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

    public void startMiddlewareServer(int port) {
        if (protocol == null)
            throw new IllegalStateException(
                "Nenhum protocolo configurado. Chame useProtocol() antes de startMiddlewareServer().");

        registerAors(port);
        protocol.init(serverRequestHandler);
        MiddlewareServer server = new MiddlewareServer(port, protocol);
        server.start();
    }

    private void registerAors(int port) {
        String host = resolveHost();
        lookup.getAll().forEach((name, provider) -> {
            AbsoluteObjectReference aor = new AbsoluteObjectReference(
                protocol.getProtocolName(), host, port, name);
            aorRegistry.put(name, aor);
            System.out.println(aor);
        });
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

    Lookup getLookup() { return lookup; }
    Invoker getInvoker() { return invoker; }

    public byte[] invokeCacheOperation(String operation, String key, byte[] value) {
        try {
            String methodPath;
            String httpMethod;
            Object[] parameters;
            Map<String, String> rawParams = new HashMap<>();

            if ("GET".equalsIgnoreCase(operation)) {
                methodPath = "get";
                httpMethod = "GET";
                parameters = new Object[]{key};
                rawParams.put("key", key);
            } else {
                methodPath = "DELETE".equalsIgnoreCase(operation) ? "delete" : "post";
                httpMethod = "POST";
                parameters = new Object[]{key, value};
                rawParams.put("key", key);
                rawParams.put("value", new String(value != null ? value : new byte[0]));
            }

            ObjectId objectId = new ObjectId("dictionary", "cache");
            InvocationRequest request = new InvocationRequest(objectId, methodPath, httpMethod, parameters, rawParams);
            InvocationReply reply = invoker.invoke(request);

            if (reply.getErrorMessage() != null) {
                throw new RuntimeException(reply.getErrorMessage());
            }

            return (byte[]) reply.getResult();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao invocar operação de cache: " + e.getMessage(), e);
        }
    }
}
