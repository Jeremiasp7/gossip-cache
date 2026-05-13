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

public class WriterServer {
    
    public static void main(String[] args) {

        try  {

            int port = Integer.parseInt(args[0]);
            String protocol = args[1];
            Integer gatewayPort = null;

            if (args.length > 2) {
                gatewayPort = Integer.parseInt(args[2]);
            }

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

            final CommunicationStrategy finalStrategy = strategy;
            GossipWorker worker = new GossipWorker(membershipList, finalStrategy, localNode, Executors.newSingleThreadScheduledExecutor());

            writeHandler.setGossipWorker(worker);
            writeHandler.setLocalNode(localNode);

            final int listenPort = port;
            System.out.println("Uma instância do Writer acaba de subir na porta " + listenPort + "!");

            new Thread(() -> finalStrategy.startListening(listenPort)).start();
            worker.startBackgroundTest();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
