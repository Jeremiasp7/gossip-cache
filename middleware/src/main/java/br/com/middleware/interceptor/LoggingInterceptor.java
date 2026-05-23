package br.com.middleware.interceptor;

public class LoggingInterceptor implements InvocationInterceptor {
    
    @Override
    public boolean before(InvocationContext context) {
        System.out.println("[LOG] " +context.getHttpMethod() 
            +" /" +context.getObjectName() +"/" +context.getMethodPath()
            +" params=" +context.getParams());
        return true;
    }

    @Override
    public void after(InvocationContext ctx, Object result) {
        System.out.println("[LOG] concluído em " + ctx.getElapsedMs() + "ms"
            + " resultado=" + result);
    }

    @Override
    public void onError(InvocationContext ctx, Exception e) {
        System.out.println("[LOG] erro após " + ctx.getElapsedMs()
            + "ms — " + e.getMessage());
    }
}
