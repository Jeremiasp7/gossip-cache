package br.com.core.model;

import java.io.Serializable;

public class AppRequest implements Serializable {
    
    private Operation operation; // type operation
    private String key; // the key of object in the cache
    private byte[] value; // the value message of the node

    // class constructors
    public AppRequest() {}

    public AppRequest(Operation operation, String key, byte[] value) { 
        this.operation = operation;
        this.key = key;
        this.value = value;
    }

    // gettes and setters
    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

}
