package br.com.gateway;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.Executors;

import br.com.core.gossip.GossipWorker;
import br.com.core.gossip.MembershipList;
import br.com.core.model.NodeInfo;
import br.com.core.model.NodeType;
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
            
            NodeInfo localNode = new NodeInfo(UUID.randomUUID(), InetAddress.getLocalHost().getHostAddress(), gatewayPort, 0, NodeType.GATEWAY);
            MembershipList membershipList = new MembershipList(localNode);

            ServiceRegistry registry = new ServiceRegistry(membershipList);
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

            GossipWorker worker = new GossipWorker(membershipList, strategy, localNode, Executors.newSingleThreadScheduledExecutor());
            gatewayRequestHandler.setMembershipList(membershipList);

            System.out.println("API Gateway no ar na porta " + gatewayPort + " roteando requisições via " + protocol.toUpperCase() + "!");
            strategy.startListening(gatewayPort);
            worker.startBackgroundTest();

        } catch (Exception e) {
            System.err.println("Erro ao iniciar o API Gateway: " + e.getMessage());
            e.printStackTrace();
        }
    }
}