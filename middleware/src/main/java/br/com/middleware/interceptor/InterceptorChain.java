package br.com.middleware.interceptor;

import java.util.ArrayList;
import java.util.List;

public class InterceptorChain {
    
    private final List<InvocationInterceptor> interceptors = new ArrayList<>();

    public void add(InvocationInterceptor interceptor) {
        interceptors.add(interceptor);
        System.out.println("[InterceptorChain] Registrado: " +interceptor.getClass().getSimpleName());
    }

    public boolean runBefore(InvocationContext context) {
        for (InvocationInterceptor interceptor : interceptors) {
            if (!interceptor.before(context)) {
                return false;
            }
        }
        
        return true;
    }

    public void runAfter(InvocationContext ctx, Object result) {
        for (InvocationInterceptor interceptor : interceptors) {
            interceptor.after(ctx, result);
        }
    }

    public void runOnError(InvocationContext ctx, Exception e) {
        for (InvocationInterceptor interceptor : interceptors) {
            interceptor.onError(ctx, e);
        }
    }
}
