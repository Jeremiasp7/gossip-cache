package br.com.reader;

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

public class ReaderServer {

    public static void main(String[] args) {

        try  {

            int port = Integer.parseInt(args[0]);
            String protocol = args[1];
            Integer gatewayPort = args.length > 2 ? Integer.parseInt(args[2]) : null;

            NodeInfo localNode = new NodeInfo(UUID.randomUUID(), InetAddress.getLocalHost().getHostAddress(), port, 0, NodeType.READER);
            MembershipList membershipList = new MembershipList(localNode);

            if (gatewayPort != null) {
                NodeInfo gatewayNode = new NodeInfo(UUID.randomUUID(), InetAddress.getLocalHost().getHostAddress(), gatewayPort, 0, NodeType.GATEWAY);
                membershipList.updateNode(gatewayNode);
                System.out.println("Gateway descoberto na porta " + gatewayPort);
            }

            DictionaryStorage dictionary = new DictionaryStorage();
            ReadRequestHandler readHandler = new ReadRequestHandler(dictionary, membershipList);
            CommunicationStrategy strategy;

            if (protocol.equalsIgnoreCase("UDP")) {
                UdpStrategy udp = new UdpStrategy(readHandler);
                strategy = udp;

            } else if (protocol.equalsIgnoreCase("TCP")) {
                HttpParser httpParser = new HttpParser();
                TcpStrategy tcp = new TcpStrategy(readHandler, httpParser);
                strategy = tcp;

            } else if (protocol.equalsIgnoreCase("GRPC")) {
                GrpcMapper grpcMapper = new GrpcMapper();
                GrpcStrategy grpc = new GrpcStrategy(readHandler, grpcMapper);
                strategy = grpc;

            } else {
                System.out.println("Método inválido.");
                return;
            }

            GossipWorker worker = new GossipWorker(membershipList, strategy, localNode, Executors.newSingleThreadScheduledExecutor());
            readHandler.setGossipWorker(worker);
            readHandler.setLocalNode(localNode);

            new Thread(() -> strategy.startListening(port)).start();
            worker.startBackgroundTest();
            System.out.println("Uma instância do Reader acaba de subir na porta " + port + "!");

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
