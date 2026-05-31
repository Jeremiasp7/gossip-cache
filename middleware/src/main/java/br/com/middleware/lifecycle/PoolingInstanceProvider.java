package br.com.middleware.lifecycle;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PoolingInstanceProvider implements InstanceProvider {

    private final BlockingQueue<Object> pool;
    private final Class<?> clazz;
    private final int size;

    public PoolingInstanceProvider(Class<?> clazz, int size) {
        this(clazz, size, () -> {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(
                    "[Pool] Falha ao criar instância de " + clazz.getSimpleName(), e);
            }
        });
    }

    public PoolingInstanceProvider(Class<?> clazz, int size, InstanceFactory factory) {
        this.clazz = clazz;
        this.size = size;
        this.pool = new LinkedBlockingQueue<>(size);
        try {
            for (int i = 0; i < size; i++) {
                pool.put(factory.create());
            }
            System.out.println("[Pool] " + size + " instâncias de "
                + clazz.getSimpleName() + " criadas");
        } catch (Exception e) {
            throw new RuntimeException(
                "[Pool] Falha ao inicializar pool de " + clazz.getSimpleName(), e);
        }
    }

    @Override
    public Object getInstance() {
        try {
            Object instance = pool.poll(5, TimeUnit.SECONDS);
            if (instance == null) {
                throw new RuntimeException(
                    "[Pool] Timeout aguardando instância de " + clazz.getSimpleName()
                    + " — pool de " + size + " instâncias esgotado");
            }
            return instance;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[Pool] Interrompido aguardando instância", e);
        }
    }

    @Override
    public void returnInstance(Object instance) {
        pool.offer(instance);
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public int getSize() {
        return size;
    }

    public int getAvailable() {
        return pool.size();
    }
}
