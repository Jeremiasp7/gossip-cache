package br.com.middleware.dto;

import java.io.Serializable;

import br.com.middleware.core.ObjectId;

public class InvocationRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private ObjectId objectId;
    private String methodPath;
    private String httpMethod;
    private Object[] parameters;

    public InvocationRequest(ObjectId objectId, String methodPath, String httpMethod, Object[] parameters) {
        this.objectId = objectId;
        this.methodPath = methodPath;
        this.httpMethod = httpMethod;
        this.parameters = parameters;
    }

    public ObjectId getObjectId() {
        return objectId;
    }

    public void setObjectId(ObjectId objectId) {
        this.objectId = objectId;
    }

    public String getMethodPath() {
        return methodPath;
    }

    public void setMethodPath(String methodPath) {
        this.methodPath = methodPath;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

}
