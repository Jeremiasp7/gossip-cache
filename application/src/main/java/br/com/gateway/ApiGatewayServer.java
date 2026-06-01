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
import br.com.core.network.TcpStrategy;
import br.com.core.network.UdpStrategy;
import br.com.middleware.core.Broker;
import br.com.middleware.interceptor.LoggingInterceptor;
import br.com.middleware.network.TcpPlugin;
import br.com.middleware.network.UdpPlugin;

public class ApiGatewayServer {

    public static void main(String[] args) {
        try {
            int middlewarePort = Integer.parseInt(args[0]);
            String protocol = args[1];
            String gatewayHost = InetAddress.getLocalHost().getHostAddress();
            UUID gatewayUUID = NodeInfo.deterministicUUID(gatewayHost, middlewarePort);
            int clusterPort = middlewarePort + 1000;

            NodeInfo localNode = new NodeInfo(
                gatewayUUID,
                gatewayHost,
                clusterPort, 0, NodeType.GATEWAY);

            MembershipList membershipList = new MembershipList(localNode);
            ServiceRegistry registry = new ServiceRegistry(membershipList);
            RequestRouter requestRouter = new RequestRouter(registry);
            GatewayRequestHandler gatewayRequestHandler = new GatewayRequestHandler(requestRouter);

            TcpStrategy tcpStrategy = null;
            UdpStrategy udpStrategy = null;
            CommunicationStrategy internalStrategy;

            if (protocol.equalsIgnoreCase("UDP")) {
                udpStrategy     = new UdpStrategy(gatewayRequestHandler);
                internalStrategy = udpStrategy;
            } else if (protocol.equalsIgnoreCase("TCP")) {
                tcpStrategy = new TcpStrategy(gatewayRequestHandler);
                internalStrategy = tcpStrategy;
            } else if (protocol.equalsIgnoreCase("GRPC")) {
                internalStrategy =
                    new GrpcStrategy(gatewayRequestHandler, new GrpcMapper());
            } else {
                System.out.println("Método inválido. Escolha UDP, TCP ou GRPC.");
                return;
            }

            requestRouter.setCommunicationStrategy(internalStrategy);

            GossipWorker worker = new GossipWorker(
                membershipList, internalStrategy, localNode,
                Executors.newSingleThreadScheduledExecutor());
            gatewayRequestHandler.setMembershipList(membershipList);
            gatewayRequestHandler.setGossipWorker(worker);

            Broker broker = Broker.createDefault();

            GatewayService gatewayService = new GatewayService(requestRouter, broker);

            broker.register(gatewayService, () -> new GatewayService(requestRouter, broker))
                .addInterceptor(new LoggingInterceptor());

            if (protocol.equalsIgnoreCase("UDP")) {
                broker.useProtocol(new UdpPlugin());
            } else {
                broker.useProtocol(new TcpPlugin());
            }

            broker.startMiddlewareServer(middlewarePort);

            if (tcpStrategy != null)
                membershipList.setOnNodeEvicted(tcpStrategy::evictPool);

            final CommunicationStrategy finalStrategy = internalStrategy;
            new Thread(() -> finalStrategy.startListening(clusterPort)).start();
            worker.startBackgroundTest();

            System.out.println("API Gateway no ar na porta de middleware " + middlewarePort
                + " e porta de cluster " + clusterPort + " via " + protocol.toUpperCase());

        } catch (Exception e) {
            System.err.println("Erro ao iniciar o API Gateway: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
