package br.com.middleware.lifecycle;

public class LazyInstanceProvider implements InstanceProvider {
    
    private final Class<?> clazz;
    private volatile Object instance; // volatitle for threads visibility

    public LazyInstanceProvider(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public Object getInstance() {
        if (instance == null) {
            synchronized (this) {
                if (instance == null) {
                    try {
                        instance = clazz.getDeclaredConstructor().newInstance();
                        System.out.println("[Lazy] Instância de "
                            +clazz.getSimpleName() +" criada na primeira requisição.");
                    } catch (Exception e) {
                        throw new RuntimeException(
                            "[Lazy] Falha ao criar instância de "
                            +clazz.getSimpleName(), e);
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
