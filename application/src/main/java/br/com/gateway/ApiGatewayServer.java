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
import br.com.middleware.core.Broker;
import br.com.middleware.interceptor.LoggingInterceptor;
import br.com.middleware.network.ProtocolPlugin;
import br.com.middleware.network.TcpPlugin;
import br.com.middleware.network.UdpPlugin;

public class ApiGatewayServer {

    public static void main(String[] args) {
        try {
            int gatewayPort = Integer.parseInt(args[0]);
            String protocol = args[1];
            String gatewayHost = InetAddress.getLocalHost().getHostAddress();
            UUID gatewayUUID = NodeInfo.deterministicUUID(gatewayHost, gatewayPort);

            NodeInfo localNode = new NodeInfo(
                gatewayUUID,
                gatewayHost,
                gatewayPort, 0, NodeType.GATEWAY);

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
                tcpStrategy     = new TcpStrategy(gatewayRequestHandler, new HttpParser());
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

            GatewayService gatewayService = new GatewayService(requestRouter, membershipList);

            ProtocolPlugin pluginImpl = protocol.equalsIgnoreCase("UDP") ? new UdpPlugin() : new TcpPlugin();

            ProtocolPlugin plugin = new Broker()
                .register(gatewayService)
                .addInterceptor(new LoggingInterceptor())
                .useProtocol(pluginImpl)
                .build(gatewayPort);

            if (tcpStrategy != null) tcpStrategy.setPlugin(plugin);
            if (udpStrategy != null) udpStrategy.setPlugin(plugin);

            if (tcpStrategy != null)
                membershipList.setOnNodeEvicted(tcpStrategy::evictPool);

            final CommunicationStrategy finalStrategy = internalStrategy;
            new Thread(() -> finalStrategy.startListening(gatewayPort)).start();
            worker.startBackgroundTest();

            System.out.println("API Gateway no ar na porta " + gatewayPort
                + " via " + protocol.toUpperCase());

        } catch (Exception e) {
            System.err.println("Erro ao iniciar o API Gateway: " + e.getMessage());
            e.printStackTrace();
        }
    }
}