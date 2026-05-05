package br.com.gateway;

import java.util.UUID;

import br.com.core.model.NodeInfo;
import br.com.core.network.CommunicationStrategy;
import br.com.core.network.GrpcMapper;
import br.com.core.network.GrpcStrategy;
import br.com.core.network.HttpParser;
import br.com.core.network.TcpStrategy;
import br.com.core.network.UdpStrategy;

public class ApiGatewayServer {
    
    public static void main(String[] args) {

        try {
            int gatewayPort = Integer.parseInt(args[0]);
            String protocol = args[1]; // UDP, TCP or gRPC
            
            ServiceRegistry registry = new ServiceRegistry();
            
            NodeInfo nodeA = new NodeInfo(UUID.randomUUID(), "127.0.0.1", 9001, 0);
            NodeInfo nodeB = new NodeInfo(UUID.randomUUID(), "127.0.0.1", 9002, 0);
            NodeInfo nodeC = new NodeInfo(UUID.randomUUID(), "127.0.0.1", 9003, 0);
            NodeInfo nodeD = new NodeInfo(UUID.randomUUID(), "127.0.0.1", 9004, 0);

            registry.registerWriter(nodeA);
            registry.registerReader(nodeB);
            registry.registerWriter(nodeC);
            registry.registerReader(nodeD);

            RequestRouter requestRouter = new RequestRouter(registry);
            GatewayRequestHandler gatewayRequestHandler = new GatewayRequestHandler(requestRouter);
            CommunicationStrategy strategy = null;

            if (protocol.equalsIgnoreCase("UDP")) {
                strategy = new UdpStrategy(gatewayRequestHandler);
                
            } else if (protocol.equalsIgnoreCase("TCP")) {
                HttpParser httpParser = new HttpParser();
                strategy = new TcpStrategy(gatewayRequestHandler, httpParser);
                
            } else if (protocol.equalsIgnoreCase("GRPC")) {
                GrpcMapper grpcMapper = new GrpcMapper();
                strategy = new GrpcStrategy(gatewayRequestHandler, grpcMapper);
                
            } else {
                System.out.println("Método inválido. Escolha UDP, TCP ou GRPC.");
                return;
            }

            requestRouter.setCommunicationStrategy(strategy);

            System.out.println("API Gateway no ar na porta " + gatewayPort + " roteando requisições via " + protocol.toUpperCase() + "!");
            strategy.startListening(gatewayPort);

        } catch (Exception e) {
            System.err.println("Erro ao iniciar o API Gateway: " + e.getMessage());
            e.printStackTrace();
        }
    }
}