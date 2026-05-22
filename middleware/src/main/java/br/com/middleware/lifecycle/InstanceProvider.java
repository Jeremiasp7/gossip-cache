package br.com.middleware.lifecycle;

public interface InstanceProvider {
    Object getInstance();
    default void returnInstance(Object instance) {} // used for the pooling
}
