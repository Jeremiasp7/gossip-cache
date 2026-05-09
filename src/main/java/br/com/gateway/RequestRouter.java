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

        System.out.println("O Gateway recebeu " + operation + " para a chave '" + request.getKey() + "'");

        switch (operation) {
            case GET:
                br.com.core.model.NodeInfo reader = serviceRegistry.getReaders();
                if (reader == null) {
                    System.out.println("  → Sem Readers disponíveis!");
                    return new AppResponse("503", null, "Serviço Indisponível - Nó não registrado");
                }
                System.out.println("  → Roteando para READER na porta " + reader.getPort());
                return communicationStrategy.sendRequest(request, reader);

            case DELETE, POST, PUT:
                br.com.core.model.NodeInfo writer = serviceRegistry.getWriters();
                if (writer == null) {
                    System.out.println("  → Sem Writers disponíveis!");
                    return new AppResponse("503", null, "Serviço Indisponível - Nó não registrado");
                }
                System.out.println("  → Roteando para WRITER na porta " + writer.getPort());
                return communicationStrategy.sendRequest(request, writer);

            default:
                return new AppResponse("400", null, "Operação não suportada");

        }

    }

}
