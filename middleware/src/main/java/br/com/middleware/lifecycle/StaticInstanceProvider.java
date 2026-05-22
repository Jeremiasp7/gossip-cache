package br.com.middleware.lifecycle;

public class StaticInstanceProvider implements InstanceProvider {
    
    private final Object instance;

    public StaticInstanceProvider(Object instance) {
        this.instance = instance;
    }

    @Override
    public Object getInstance() {
        return instance;
    }
}
