package br.com.middleware.lifecycle;

public class PerRequestInstanceProvider implements InstanceProvider {

    private final Class<?> clazz;
    private final InstanceFactory factory;

    public PerRequestInstanceProvider(Class<?> clazz) {
        this(clazz, () -> {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(
                    "[PerRequest] Falha ao criar uma instância de "
                    + clazz.getSimpleName(), e);
            }
        });
    }

    public PerRequestInstanceProvider(Class<?> clazz, InstanceFactory factory) {
        this.clazz = clazz;
        this.factory = factory;
    }

    @Override
    public Object getInstance() {
        try {
            return factory.create();
        } catch (Exception e) {
            throw new RuntimeException(
                "[PerRequest] Falha ao criar uma instância de "
                + clazz.getSimpleName(), e);
        }
    }

    public Class<?> getClazz() {
        return clazz;
    }
}
