package br.com.middleware.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import br.com.middleware.annotations.RemoteObject;
import br.com.middleware.lifecycle.InstanceProvider;
import br.com.middleware.lifecycle.LazyInstanceProvider;
import br.com.middleware.lifecycle.Lifecycle;
import br.com.middleware.lifecycle.LifecycleMode;
import br.com.middleware.lifecycle.PerRequestInstanceProvider;
import br.com.middleware.lifecycle.PoolingInstanceProvider;
import br.com.middleware.lifecycle.StaticInstanceProvider;

public class Lookup {
    
    private final Map<String, InstanceProvider> registry = new ConcurrentHashMap<>();

    public void register(Object object) {
        Class<?> clazz = object.getClass();
        RemoteObject annotation = clazz.getAnnotation(RemoteObject.class);

        if (annotation == null) {
            throw new IllegalArgumentException(
                "Classe " +clazz.getSimpleName() +" não possui @RemoteObject"
            );
        }

        String name = annotation.name().isEmpty() ? clazz.getSimpleName().toLowerCase() : annotation.name();

        Lifecycle lifecycle = clazz.getAnnotation(Lifecycle.class);
        InstanceProvider provider;

        if (lifecycle == null || lifecycle.value() == LifecycleMode.STATIC) {
            provider = new StaticInstanceProvider(object);
            System.out.println("[Lookup] " +name +" -> STATIC");
        } else if (lifecycle.value() == LifecycleMode.PER_REQUEST) {
            provider = new PerRequestInstanceProvider(clazz);
            System.out.println("[Lookup] " +name +" -> PER_REQUEST");
        } else if (lifecycle.value() == LifecycleMode.LAZY) {
            provider = new LazyInstanceProvider(clazz);
            System.out.println("[Lookup] " +name +" -> LAZY");
        } else if (lifecycle.value() == LifecycleMode.POOLING) {
            provider = new PoolingInstanceProvider(clazz, lifecycle.poolSize());
            System.out.println("[Lookup] " +name +" -> LAZY");
        } else {
            provider = new StaticInstanceProvider(object); // fallback
        }

        registry.put(name, provider);
    }

    public Object find(String name) {
        InstanceProvider provider = registry.get(name);
        if (provider == null)
            throw new RuntimeException("[Lookup] Objeto não encontrado: " + name);
        return provider.getInstance();
    }

    public InstanceProvider findProvider(String name) {
        InstanceProvider provider = registry.get(name);
        if (provider == null)
            throw new RuntimeException("[Lookup] Provider não encontrado: " + name);
        return provider;
    }

    public Class<?> findClass(String name) {
        InstanceProvider provider = findProvider(name);
        if (provider instanceof StaticInstanceProvider)
            return ((StaticInstanceProvider) provider).getInstance().getClass();
        if (provider instanceof PerRequestInstanceProvider)
            return ((PerRequestInstanceProvider) provider).getClazz();
        if (provider instanceof LazyInstanceProvider)
            return ((LazyInstanceProvider) provider).getClazz();
        if (provider instanceof PoolingInstanceProvider)
            return ((PoolingInstanceProvider) provider).getClazz();
        throw new RuntimeException("Provider desconhecido para: " + name);
    }

    public Map<String, InstanceProvider> getAll() {
        return registry;
    }
}
