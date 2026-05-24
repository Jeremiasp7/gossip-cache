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

        try  {

            int port = Integer.parseInt(args[0]);
            String protocol = args[1];
            Integer gatewayPort = args.length > 2 ? Integer.parseInt(args[2]) : null;

            NodeInfo localNode = new NodeInfo(UUID.randomUUID(), InetAddress.getLocalHost().getHostAddress(), port, 0, NodeType.WRITER);
            MembershipList membershipList = new MembershipList(localNode);

            if (gatewayPort != null) {
                NodeInfo gatewayNode = new NodeInfo(UUID.randomUUID(), InetAddress.getLocalHost().getHostAddress(), gatewayPort, 0, NodeType.GATEWAY);
                membershipList.updateNode(gatewayNode);
                System.out.println("Gateway descoberto na porta " + gatewayPort);
            }

            DictionaryStorage dictionary = new DictionaryStorage();
            WriterRequestHandler writeHandler = new WriterRequestHandler(dictionary, membershipList);
            CommunicationStrategy strategy;

            if (protocol.equalsIgnoreCase("UDP")) {
                UdpStrategy udp = new UdpStrategy(writeHandler);
                strategy = udp;

            } else if (protocol.equalsIgnoreCase("TCP")) {
                HttpParser httpParser = new HttpParser();
                TcpStrategy tcp = new TcpStrategy(writeHandler, httpParser);
                strategy = tcp;

            } else if (protocol.equalsIgnoreCase("GRPC")) {
                GrpcMapper grpcMapper = new GrpcMapper();
                GrpcStrategy grpc = new GrpcStrategy(writeHandler, grpcMapper);
                strategy = grpc;

            } else {
                System.out.println("Método inválido.");
                return;
            }

            GossipWorker worker = new GossipWorker(membershipList, strategy, localNode, Executors.newSingleThreadScheduledExecutor());
            writeHandler.setGossipWorker(worker);
            writeHandler.setLocalNode(localNode);

            new Thread(() -> strategy.startListening(port)).start();
            worker.startBackgroundTest();

            System.out.println("Writer no ar na porta " +port +" via " +protocol.toUpperCase());

            ProtocolPlugin plugin = protocol.equalsIgnoreCase("UDP") ? new UdpPlugin() : new TcpPlugin();

            new Broker()
                .register(dictionary)
                .addInterceptor(new LoggingInterceptor())
                .useProtocol(plugin)
                .start(port);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
