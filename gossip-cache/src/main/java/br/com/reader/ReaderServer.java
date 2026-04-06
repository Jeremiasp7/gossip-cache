package br.com.reader;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.Executors;

import br.com.core.gossip.GossipWorker;
import br.com.core.gossip.MembershipList;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.NodeInfo;
import br.com.core.network.CommunicationStrategy;
import br.com.core.network.GrpcMapper;
import br.com.core.network.GrpcStrategy;
import br.com.core.network.TcpStrategy;
import br.com.core.network.UdpStrategy;

public class ReaderServer {

    public static void main(String[] args) {
            
        try  {

            NodeInfo node = new NodeInfo(UUID.randomUUID(), InetAddress.getLocalHost().getHostAddress(), Integer.parseInt(args[0]), 0);
            DictionaryStorage dictionary = new DictionaryStorage();
            ReadRequestHandler readHandler = new ReadRequestHandler(dictionary);

            CommunicationStrategy strategy;

            if (args[1].equalsIgnoreCase("UDP")) {
                UdpStrategy udp = new UdpStrategy(readHandler);
                strategy = udp;

            } else if (args[1].equalsIgnoreCase("TCP")) {
                TcpStrategy tcp = new TcpStrategy(readHandler);
                strategy = tcp;

            } else if (args[1].equalsIgnoreCase("GRPC")) {
                GrpcMapper grpcMapper = new GrpcMapper();
                GrpcStrategy grpc = new GrpcStrategy(readHandler, grpcMapper);
                strategy = grpc;

            } else {
                System.out.println("Método inválido.");
                return;
            }

            MembershipList membershipList = new MembershipList(node);
            GossipWorker worker = new GossipWorker(membershipList, strategy, node, Executors.newSingleThreadScheduledExecutor());
            
            strategy.startListening(Integer.parseInt(args[0]));
            worker.startBackgroundTest();

            System.out.println("Uma instância do Reader acaba de subir!");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
