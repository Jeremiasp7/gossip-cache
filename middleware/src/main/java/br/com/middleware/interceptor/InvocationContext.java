package br.com.middleware.interceptor;

import java.util.HashMap;
import java.util.Map;

public class InvocationContext {
    
    private final String objectName;
    private final String methodPath;
    private final String httpMethod;
    private final Map<String, String> params;
    private final Map<String, Object> attributes = new HashMap<>();
    private final long startTime = System.currentTimeMillis();

    public InvocationContext(String objectName, String methodPath, String httpMethod, Map<String, String> params) {
        this.objectName = objectName;
        this.methodPath = methodPath;
        this.httpMethod = httpMethod;
        this.params = params;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public long getElapsedMs() {
        return System.currentTimeMillis() - startTime;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getMethodPath() {
        return methodPath;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public Map<String, String> getParams() {
        return params;
    }
}
