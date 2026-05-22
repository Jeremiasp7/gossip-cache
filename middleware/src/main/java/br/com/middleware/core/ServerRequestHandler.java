package br.com.middleware.core;

import br.com.middleware.dto.InvocationReply;
import br.com.middleware.dto.InvocationRequest;

public class ServerRequestHandler {

    private final Invoker invoker;
    private final Marshaller marshaller;

    public ServerRequestHandler(Invoker invoker, Marshaller marshaller) {
        this.invoker = invoker;
        this.marshaller = marshaller;
    }

    public String handle(InvocationRequest request) {
        InvocationReply reply = invoker.invoke(request);
        if (reply.getErrorMessage() != null)
            return "{\"error\": \"" + reply.getErrorMessage() + "\"}";
        return marshaller.marshal(reply.getResult());
    }
}
