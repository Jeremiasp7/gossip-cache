package br.com.middleware.core;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import br.com.middleware.annotations.MethodMapping;
import br.com.middleware.annotations.RemoteObject;
import br.com.middleware.dto.InvocationReply;
import br.com.middleware.dto.InvocationRequest;

public class Invoker {
    
    private Map<String, Object> remoteObjectsRegistry;

    public Invoker() {
        this.remoteObjectsRegistry = new ConcurrentHashMap<>();
    }

    public void register(Object remoteObject) { // registry remote objects before the server begin to listen
        Class<?> clazz = remoteObject.getClass();

        if (clazz.isAnnotationPresent(RemoteObject.class)) {
            RemoteObject annotation = clazz.getAnnotation(RemoteObject.class);
            String objectName = annotation.name();
            
            remoteObjectsRegistry.put(objectName, remoteObject);
            System.out.println("[Invoker] Objeto Remoto registrado: " + objectName);
        } else {
            throw new IllegalArgumentException("A classe não possui a anotação @RemoteObject");
        }
    }

    public InvocationReply invoke(InvocationRequest request) {
        try {
            String objectName = request.getObjectId().getResourceName();
            Object remoteObject = remoteObjectsRegistry.get(objectName);

            if (remoteObject == null)
                throw new RuntimeException("Objeto não registrado: " + objectName);

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
                throw new RuntimeException("Método não encontrado: " + request.getMethodPath());

            Object result = methodToInvoke.invoke(remoteObject, request.getParameters());
            return new InvocationReply(result, null);

        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return new InvocationReply(null, msg);
        }
    }
}
