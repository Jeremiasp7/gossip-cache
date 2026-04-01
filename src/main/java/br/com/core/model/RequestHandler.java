package model;

public interface RequestHandler {
    
    public AppResponse handleRequest(AppRequest request); // process the request through you own rules
    
}
