package br.com.middleware.core;

import java.lang.reflect.Method;

import br.com.middleware.annotations.MethodMapping;
import br.com.middleware.dto.InvocationReply;
import br.com.middleware.dto.InvocationRequest;

public class Invoker {

    private final Lookup lookup;

    public Invoker(Lookup lookup) {
        this.lookup = lookup;
    }

    public InvocationReply invoke(InvocationRequest request) {
        try {
            Object remoteObject = lookup.find(
                request.getObjectId().getResourceName());

            Method methodToInvoke = null;
            for (Method m : remoteObject.getClass().getDeclaredMethods()) {
                MethodMapping mm = m.getAnnotation(MethodMapping.class);
                if (mm == null) continue;
                if (mm.path().equals(request.getMethodPath()) &&
                    mm.method().name().equalsIgnoreCase(request.getHttpMethod())) {
                    methodToInvoke = m;
                    break;
                }
            }

            if (methodToInvoke == null)
                throw new RuntimeException(
                    "Método não encontrado: " + request.getMethodPath());

            Object result = methodToInvoke.invoke(remoteObject, request.getParameters());
            return new InvocationReply(result, null);

        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return new InvocationReply(null, msg);
        }
    }
}