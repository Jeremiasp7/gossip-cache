package br.com.middleware.lifecycle;

public class LazyInstanceProvider implements InstanceProvider {

    private final Class<?> clazz;
    private final InstanceFactory factory;
    private volatile Object instance;

    public LazyInstanceProvider(Class<?> clazz) {
        this(clazz, () -> {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(
                    "[Lazy] Falha ao criar instância de "
                    + clazz.getSimpleName(), e);
            }
        });
    }

    public LazyInstanceProvider(Class<?> clazz, InstanceFactory factory) {
        this.clazz = clazz;
        this.factory = factory;
    }

    @Override
    public Object getInstance() {
        if (instance == null) {
            synchronized (this) {
                if (instance == null) {
                    try {
                        instance = factory.create();
                        System.out.println("[Lazy] Instância de "
                            + clazz.getSimpleName() + " criada na primeira requisição.");
                    } catch (Exception e) {
                        throw new RuntimeException(
                            "[Lazy] Falha ao criar instância de "
                            + clazz.getSimpleName(), e);
                    }
                }
            }
        }
        return instance;
    }

    public Class<?> getClazz() {
        return clazz;
    }
}
