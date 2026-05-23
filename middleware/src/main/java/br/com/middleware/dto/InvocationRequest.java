package br.com.middleware.dto;

import java.io.Serializable;
import java.util.Map;

import br.com.middleware.core.ObjectId;

public class InvocationRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private ObjectId objectId;
    private String methodPath;
    private String httpMethod;
    private Object[] parameters;
    private final Map<String, String> rawParams;

    public InvocationRequest(ObjectId objectId, String methodPath, String httpMethod, Object[] parameters, Map<String, String> rawParams) {
        this.objectId = objectId;
        this.methodPath = methodPath;
        this.httpMethod = httpMethod;
        this.parameters = parameters;
        this.rawParams = rawParams;
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

    public Map<String, String> getParamsAsMap() {
        return rawParams;
    }
}
