package br.com.writer;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.Executors;

import br.com.core.gossip.GossipWorker;
import br.com.core.gossip.MembershipList;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.NodeInfo;
import br.com.core.model.NodeType;
import br.com.core.network.CommunicationStrategy;
import br.com.core.network.GrpcMapper;
import br.com.core.network.GrpcStrategy;
import br.com.core.network.TcpStrategy;
import br.com.core.network.UdpStrategy;
import br.com.middleware.core.Broker;
import br.com.middleware.core.ServerRequestHandler;
import br.com.middleware.interceptor.LoggingInterceptor;
import br.com.middleware.network.TcpPlugin;
import br.com.middleware.network.UdpPlugin;

public class WriterServer {

    public static void main(String[] args) {
        try {
            int middlewarePort  = Integer.parseInt(args[0]);
            String protocol     = args[1];
            Integer gatewayPort = args.length > 2 ? Integer.parseInt(args[2]) : null;
            int clusterPort     = middlewarePort + 1000;

            NodeInfo localNode = new NodeInfo(
                UUID.randomUUID(),
                InetAddress.getLocalHost().getHostAddress(),
                clusterPort, 0, NodeType.WRITER);

            MembershipList membershipList = new MembershipList(localNode);

            if (gatewayPort != null) {
                String gatewayHost     = InetAddress.getLocalHost().getHostAddress();
                UUID gatewayUUID       = NodeInfo.deterministicUUID(gatewayHost, gatewayPort);
                int gatewayClusterPort = gatewayPort + 1000;
                NodeInfo gatewayNode   = new NodeInfo(
                    gatewayUUID, gatewayHost, gatewayClusterPort, 0, NodeType.GATEWAY);
                membershipList.updateNode(gatewayNode);
                System.out.println("Gateway descoberto na porta de cluster " + gatewayClusterPort);
            }

            DictionaryStorage dictionary = new DictionaryStorage();
            Broker broker = Broker.createDefault()
                .register(dictionary)
                .addInterceptor(new LoggingInterceptor());

            ServerRequestHandler srh = broker.getServerRequestHandler();

            WriterRequestHandler writeHandler =
                new WriterRequestHandler(dictionary, membershipList, srh);

            TcpStrategy tcpStrategy = null;
            UdpStrategy udpStrategy = null;
            CommunicationStrategy strategy;

            if (protocol.equalsIgnoreCase("UDP")) {
                udpStrategy = new UdpStrategy(writeHandler);
                strategy    = udpStrategy;
            } else if (protocol.equalsIgnoreCase("TCP")) {
                tcpStrategy = new TcpStrategy(writeHandler);
                strategy    = tcpStrategy;
            } else if (protocol.equalsIgnoreCase("GRPC")) {
                strategy = new GrpcStrategy(writeHandler, new GrpcMapper());
            } else {
                System.out.println("Método inválido.");
                return;
            }

            GossipWorker worker = new GossipWorker(
                membershipList, strategy, localNode,
                Executors.newSingleThreadScheduledExecutor());
            writeHandler.setGossipWorker(worker);
            writeHandler.setLocalNode(localNode);

            if (protocol.equalsIgnoreCase("UDP")) broker.useProtocol(new UdpPlugin());
            else broker.useProtocol(new TcpPlugin());

            broker.startMiddlewareServer(middlewarePort);

            if (tcpStrategy != null)
                membershipList.setOnNodeEvicted(tcpStrategy::evictPool);

            final CommunicationStrategy finalStrategy = strategy;
            new Thread(() -> finalStrategy.startListening(clusterPort)).start();
            worker.startBackgroundTest();

            System.out.println("Writer: middleware=" + middlewarePort
                + " cluster=" + clusterPort + " via " + protocol.toUpperCase());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}