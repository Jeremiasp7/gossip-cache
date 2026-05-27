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
import br.com.core.network.HttpParser;
import br.com.core.network.TcpStrategy;
import br.com.core.network.UdpStrategy;
import br.com.middleware.core.Broker;
import br.com.middleware.interceptor.LoggingInterceptor;
import br.com.middleware.network.ProtocolPlugin;
import br.com.middleware.network.TcpPlugin;
import br.com.middleware.network.UdpPlugin;

public class WriterServer {

    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(args[0]);
            String protocol = args[1];
            Integer gatewayPort = args.length > 2 ? Integer.parseInt(args[2]) : null;

            NodeInfo localNode = new NodeInfo(
                UUID.randomUUID(),
                InetAddress.getLocalHost().getHostAddress(),
                port, 0, NodeType.WRITER);

            MembershipList membershipList = new MembershipList(localNode);

            if (gatewayPort != null) {
                String gatewayHost = InetAddress.getLocalHost().getHostAddress();
                UUID gatewayUUID   = NodeInfo.deterministicUUID(gatewayHost, gatewayPort);
                NodeInfo gatewayNode = new NodeInfo(
                    gatewayUUID, gatewayHost, gatewayPort, 0, NodeType.GATEWAY);
                membershipList.updateNode(gatewayNode);
                System.out.println("Gateway descoberto na porta " + gatewayPort);
            }

            DictionaryStorage dictionary = new DictionaryStorage();
            WriterRequestHandler writeHandler = new WriterRequestHandler(dictionary, membershipList);

            TcpStrategy tcpStrategy = null;
            UdpStrategy udpStrategy = null;
            CommunicationStrategy strategy;

            if (protocol.equalsIgnoreCase("UDP")) {
                udpStrategy = new UdpStrategy(writeHandler);
                strategy    = udpStrategy;
            } else if (protocol.equalsIgnoreCase("TCP")) {
                tcpStrategy = new TcpStrategy(writeHandler, new HttpParser());
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

            ProtocolPlugin pluginImpl = protocol.equalsIgnoreCase("UDP") ? new UdpPlugin() : new TcpPlugin();

            ProtocolPlugin plugin = new Broker()
                .register(dictionary)
                .addInterceptor(new LoggingInterceptor())
                .useProtocol(pluginImpl)
                .build(port);

            if (tcpStrategy != null) tcpStrategy.setPlugin(plugin);
            if (udpStrategy != null) udpStrategy.setPlugin(plugin);

            if (tcpStrategy != null)
                membershipList.setOnNodeEvicted(tcpStrategy::evictPool);

            final CommunicationStrategy finalStrategy = strategy;
            new Thread(() -> finalStrategy.startListening(port)).start();
            worker.startBackgroundTest();

            System.out.println("Writer no ar na porta " + port
                + " via " + protocol.toUpperCase());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}