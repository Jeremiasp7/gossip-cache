package br.com.middleware.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import br.com.middleware.annotations.RemoteObject;

public class Lookup {
    
    private final Map<String, Object> registry = new ConcurrentHashMap<>();

    public void register(Object object) {
        Class<?> clazz = object.getClass();
        RemoteObject annotation = clazz.getAnnotation(RemoteObject.class);

        if (annotation == null) {
            throw new IllegalArgumentException(
                "Classe " +clazz.getSimpleName() +" não possui @RemoteObject"
            );
        }

        String name = annotation.name().isEmpty() ? clazz.getSimpleName().toLowerCase() : annotation.name();

        registry.put(name, object);
        System.out.println("[Lookup] Registrado: " +name +" " +clazz.getSimpleName());
    }

    public Object find(String name) {
        Object obj = registry.get(name);
        if (obj == null)
            throw new RuntimeException("[Lookup] Objeto não encontrado: " + name);
        return obj;
    }

    public Class<?> findClass(String name) {
        return find(name).getClass();
    }

    public Map<String, Object> getAll() {
        return registry;
    }
}
