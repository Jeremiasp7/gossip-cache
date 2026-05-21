package br.com.middleware.core;

import br.com.middleware.annotations.RemoteObject;

public class AbsoluteObjectReference {

    private final String protocol;
    private final String host;
    private final int port;
    private final String objectName;

    public AbsoluteObjectReference(String protocol, String host,
                                   int port, String objectName) {
        this.protocol   = protocol;
        this.host       = host;
        this.port       = port;
        this.objectName = objectName;
    }

    // Constrói o AOR a partir de um objeto já anotado
    public static AbsoluteObjectReference from(Object obj, String protocol,
                                               String host, int port) {
        RemoteObject ann = obj.getClass().getAnnotation(RemoteObject.class);
        if (ann == null)
            throw new IllegalArgumentException("Objeto sem @RemoteObject");

        String name = ann.name().isEmpty()
            ? obj.getClass().getSimpleName().toLowerCase()
            : ann.name();

        return new AbsoluteObjectReference(protocol, host, port, name);
    }

    // URL base do objeto — o cliente acrescenta /methodPath
    public String toBaseUrl() {
        return protocol + "://" + host + ":" + port + "/" + objectName;
    }

    // URL completa para um método específico
    public String toMethodUrl(String methodPath) {
        return toBaseUrl() + "/" + methodPath;
    }

    @Override
    public String toString() {
        return "[AOR] " + toBaseUrl();
    }

    // getters
    public String getProtocol() { return protocol; }
    public String getHost()     { return host; }
    public int    getPort()     { return port; }
    public String getObjectName() { return objectName; }
}