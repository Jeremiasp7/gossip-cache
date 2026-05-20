package br.com.middleware.core;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import br.com.middleware.annotations.MethodMapping;
import br.com.middleware.annotations.Param;
import br.com.middleware.dto.InvocationRequest;

public class Marshaller {

    public InvocationRequest unmarshal(String httpMethod, String objectName,
                                       String methodPath, Map<String, String> params,
                                       Class<?> targetClass) throws Exception {
        Method targetMethod = null;
        for (Method m : targetClass.getDeclaredMethods()) {
            MethodMapping mm = m.getAnnotation(MethodMapping.class);
            if (mm != null
                    && mm.path().equals(methodPath)
                    && mm.method().name().equalsIgnoreCase(httpMethod)) {
                targetMethod = m;
                break;
            }
        }
        if (targetMethod == null)
            throw new RuntimeException("Método não encontrado: " + methodPath);

        Parameter[] parameters = targetMethod.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Param p = parameters[i].getAnnotation(Param.class);
            if (p == null)
                throw new RuntimeException("Parâmetro sem @Param: "
                        + parameters[i].getName());
            args[i] = convert(params.get(p.name()), parameters[i].getType());
        }

        ObjectId objectId = new ObjectId(objectName, methodPath);
        return new InvocationRequest(objectId, methodPath, httpMethod, args);
    }

    public String marshal(Object result) {
        if (result == null) return "null";
        if (result instanceof byte[]) return new String((byte[]) result, StandardCharsets.UTF_8);
        return result.toString();
    }

    private Object convert(String raw, Class<?> type) {
        if (raw == null) return null;
        if (type == int.class || type == Integer.class) return Integer.parseInt(raw);
        if (type == long.class || type == Long.class)   return Long.parseLong(raw);
        if (type == boolean.class)                       return Boolean.parseBoolean(raw);
        if (type == byte[].class) return raw.getBytes(StandardCharsets.UTF_8);
        return raw;
    }
}