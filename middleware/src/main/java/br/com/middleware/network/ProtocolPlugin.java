package br.com.middleware.network;

import br.com.middleware.core.Marshaller;
import br.com.middleware.core.ServerRequestHandler;

public interface ProtocolPlugin {
    void start(int port, ServerRequestHandler ServerRequestHandler, Marshaller marshaller);
    String getProtocolName();
}
