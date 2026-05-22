package br.com.middleware.lifecycle;

public class PerRequestInstanceProvider implements InstanceProvider{
    
    private final Class<?> clazz;

    public PerRequestInstanceProvider(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public Object getInstance() {
        try {
            return clazz.getDeclaredConstructor().newInstance();
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
