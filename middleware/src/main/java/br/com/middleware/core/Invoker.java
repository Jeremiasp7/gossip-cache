package br.com.middleware.core;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import br.com.middleware.annotations.RemoteObject;
import br.com.middleware.dto.InvocationReply;
import br.com.middleware.dto.InvocationRequest;

public class Invoker {
    
    private Marshaller marshaller;
    private Map<String, Object> remoteObjectsRegistry;

    public Invoker() {
        this.marshaller = new Marshaller();
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

    public byte[] invoke(byte[] requestBytes) { // get the bytes from the srh and return the response in bytes
        InvocationReply reply;

        try {
            InvocationRequest request = marshaller.unmarshal(requestBytes);

            String targetObjectId = request.getObjectId().getUniqueId().toString();
            Object remoteObject = remoteObjectsRegistry.get(targetObjectId);

            Method methodToInvoke = null;
            for (Method method : remoteObject.getClass().getMethods()) {
                if (method.getName().equals(request.getMethodName())) {
                    methodToInvoke = method;
                    break;
                }
            }

            if (methodToInvoke == null) {
                throw new RuntimeException("Método não encontrado: " + request.getMethodName());
            }

            // the dictionary storage method is executed
            Object result = methodToInvoke.invoke(remoteObject, request.getParameters()); 
            reply = new InvocationReply(result, null);
        } catch (Exception e) {
            String errorMessage = (e.getCause() != null) ? e.getCause().getMessage() : e.getMessage();
            System.err.println("[Invoker] Erro na invocação remota: " + errorMessage);

            reply = new InvocationReply(null, errorMessage);
        }

        try {
            return marshaller.marshal(reply);
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0]; 
        }
    }
}
