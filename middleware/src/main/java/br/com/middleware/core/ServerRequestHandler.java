package br.com.middleware.core;

import br.com.middleware.dto.InvocationReply;
import br.com.middleware.dto.InvocationRequest;
import br.com.middleware.interceptor.InterceptorChain;
import br.com.middleware.interceptor.InvocationContext;

public class ServerRequestHandler {

    private final Invoker invoker;
    private final Marshaller marshaller;
    private final InterceptorChain interceptorChain;

    public ServerRequestHandler(Invoker invoker, Marshaller marshaller, InterceptorChain interceptionChain) {
        this.invoker = invoker;
        this.marshaller = marshaller;
        this.interceptorChain = interceptionChain;
    }

    public String handle(InvocationRequest request) {

        InvocationContext ctx = new InvocationContext(
            request.getObjectId().getResourceName(),
            request.getMethodPath(),
            request.getHttpMethod(),
            request.getParamsAsMap()
        );

        if (!interceptorChain.runBefore(ctx)) {
            String err = (String) ctx.getAttribute("authError");
            return "{\"error\": \""
                + (err != null ? err : "Requisição bloqueada") + "\"}";
        }

        InvocationReply reply = invoker.invoke(request);

        if (reply.getErrorMessage() != null) {
            interceptorChain.runOnError(ctx,
                new RuntimeException(reply.getErrorMessage()));
            return "{\"error\": \"" + reply.getErrorMessage() + "\"}";
        }

        interceptorChain.runAfter(ctx, reply.getResult());
        return marshaller.marshal(reply.getResult());
    }
}
