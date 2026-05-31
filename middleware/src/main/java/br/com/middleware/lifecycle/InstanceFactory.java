package br.com.middleware.lifecycle;

@FunctionalInterface
public interface InstanceFactory {
    Object create();
}
