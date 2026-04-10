package br.com.gateway;

import java.util.UUID;

import br.com.core.model.NodeInfo;
import br.com.core.network.CommunicationStrategy;
import br.com.core.network.GrpcMapper;
import br.com.core.network.GrpcStrategy;
import br.com.core.network.TcpStrategy;
import br.com.core.network.UdpStrategy;

public class ApiGatewayServer {
    
    public static void main(String[] args) {

        try {
            int gatewayPort = Integer.parseInt(args[0]);
            String protocol = args[1]; // UDP, TCP or gRPC
            
            ServiceRegistry registry = new ServiceRegistry();
            
            NodeInfo nodeA = new NodeInfo(UUID.randomUUID(), "127.0.0.1", 9001, 0);
            NodeInfo nodeB = new NodeInfo(UUID.randomUUID(), "127.0.0.2", 9002, 0);

            registry.registerWriter(nodeA);
            registry.registerReader(nodeB);

            RequestRouter requestRouter = new RequestRouter(registry);
            GatewayRequestHandler gatewayRequestHandler = new GatewayRequestHandler(requestRouter);
            CommunicationStrategy strategy = null;

            if (protocol.equalsIgnoreCase("UDP")) {
                strategy = new UdpStrategy(gatewayRequestHandler);
                
            } else if (protocol.equalsIgnoreCase("TCP")) {
                strategy = new TcpStrategy(gatewayRequestHandler);
                
            } else if (protocol.equalsIgnoreCase("GRPC")) {
                GrpcMapper grpcMapper = new GrpcMapper();
                strategy = new GrpcStrategy(gatewayRequestHandler, grpcMapper);
                
            } else {
                System.out.println("Método inválido. Escolha UDP, TCP ou GRPC.");
                return;
            }

            requestRouter.setCommunicationStrategy(strategy);

            strategy.startListening(gatewayPort);
            
            System.out.println("API Gateway no ar na porta " + gatewayPort + " roteando requisições via " + protocol.toUpperCase() + "!");

        } catch (Exception e) {
            System.err.println("Erro ao iniciar o API Gateway: " + e.getMessage());
            e.printStackTrace();
        }
    }
}