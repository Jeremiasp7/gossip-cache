package br.com.gateway;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.Operation;
import br.com.core.network.CommunicationStrategy;

public class RequestRouter {
    
    private ServiceRegistry serviceRegistry;
    private CommunicationStrategy communicationStrategy;

    public RequestRouter(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    public void setCommunicationStrategy(CommunicationStrategy communicationStrategy) {
        this.communicationStrategy = communicationStrategy;
    }

    public AppResponse routeRequest(AppRequest request) {
        
        Operation operation = request.getOperation();

        switch (operation) {
            case GET:
                return communicationStrategy.sendRequest(request, serviceRegistry.getReader());
            
            case DELETE, POST, PUT:
                if (serviceRegistry.getWriter() == null) {
                    return new AppResponse("503", null, "Serviço Indisponível - Nó não registrado");
                }
                return communicationStrategy.sendRequest(request, serviceRegistry.getWriter());
        
            default:
                return new AppResponse("400", null, "Operação não suportada");
                
        }

    }

}
