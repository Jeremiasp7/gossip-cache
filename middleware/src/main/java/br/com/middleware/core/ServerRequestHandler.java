package br.com.middleware.core;

import java.util.Map;

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

    public String handle(String httpMethod, String objectName, String methodPath, Map<String, String> params) {
        try {
            InvocationRequest request = marshaller.unmarshal(httpMethod, objectName, methodPath, params);

            InvocationContext ctx = new InvocationContext(
                request.getObjectId().getResourceName(),
                request.getMethodPath(),
                request.getHttpMethod(),
                request.getParamsAsMap()
            );

            interceptorChain.runBefore(ctx);

            InvocationReply reply = invoker.invoke(request);

            if (reply.getErrorMessage() != null) {
                interceptorChain.runOnError(ctx, new RuntimeException(reply.getErrorMessage()));
                return "{\"error\": \"" + reply.getErrorMessage() + "\"}";
            }

            interceptorChain.runAfter(ctx, reply.getResult());

            return marshaller.marshal(reply.getResult());

        } catch (Exception e) {
            return "{\"error\": \"Erro interno: " + e.getMessage() + "\"}";
        }
    }
}
