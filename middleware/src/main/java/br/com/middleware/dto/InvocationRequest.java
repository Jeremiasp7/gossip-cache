package br.com.middleware.dto;

import java.io.Serializable;

import br.com.middleware.core.ObjectId;

public class InvocationRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private ObjectId objectId;
    private String methodName; 
    private Object[] parameters;

    public InvocationRequest(ObjectId objectId, String methodName, Object[] parameters) {
        this.objectId = objectId;
        this.methodName = methodName;
        this.parameters = parameters;
    }

    public ObjectId getObjectId() {
        return objectId;
    }

    public void setObjectId(ObjectId objectId) {
        this.objectId = objectId;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }

    
}
