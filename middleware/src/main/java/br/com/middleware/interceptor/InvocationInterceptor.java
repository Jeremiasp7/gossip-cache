package br.com.middleware.interceptor;

public interface InvocationInterceptor {
    
    boolean before(InvocationContext context); // returns false for interrupt the chain
    void after(InvocationContext context, Object result);
    void onError(InvocationContext context, Exception e);
}
