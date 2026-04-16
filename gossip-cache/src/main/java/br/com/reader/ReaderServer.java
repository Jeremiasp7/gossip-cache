package br.com.reader;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.core.gossip.GossipWorker;
import br.com.core.gossip.MembershipList;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.NodeInfo;
import br.com.core.network.CommunicationStrategy;
import br.com.core.network.GrpcMapper;
import br.com.core.network.GrpcStrategy;
import br.com.core.network.HttpParser;
import br.com.core.network.TcpStrategy;
import br.com.core.network.UdpStrategy;

public class ReaderServer {

    public static void main(String[] args) {

        Logger.getLogger("io.grpc").setLevel(Level.WARNING);
        Logger.getLogger("io.netty").setLevel(Level.WARNING);
            
        try  {

            NodeInfo readerNode = new NodeInfo(UUID.randomUUID(), InetAddress.getLocalHost().getHostAddress(), Integer.parseInt(args[0]), 0);
            NodeInfo writerNode = new NodeInfo(UUID.randomUUID(), "127.0.0.1", 9001, 0);

            MembershipList membershipList = new MembershipList(readerNode);
            membershipList.updateNode(writerNode);
            
            DictionaryStorage dictionary = new DictionaryStorage();
            ReadRequestHandler readHandler = new ReadRequestHandler(dictionary, membershipList);

            CommunicationStrategy strategy;

            if (args[1].equalsIgnoreCase("UDP")) {
                UdpStrategy udp = new UdpStrategy(readHandler);
                strategy = udp;

            } else if (args[1].equalsIgnoreCase("TCP")) {
                HttpParser httpParser = new HttpParser();
                TcpStrategy tcp = new TcpStrategy(readHandler, httpParser);
                strategy = tcp;

            } else if (args[1].equalsIgnoreCase("GRPC")) {
                GrpcMapper grpcMapper = new GrpcMapper();
                GrpcStrategy grpc = new GrpcStrategy(readHandler, grpcMapper);
                strategy = grpc;

            } else {
                System.out.println("Método inválido.");
                return;
            }

            GossipWorker worker = new GossipWorker(membershipList, strategy, readerNode, Executors.newSingleThreadScheduledExecutor());
            
            System.out.println("Uma instância do Reader acaba de subir na porta " + args[0] + "!");
            strategy.startListening(Integer.parseInt(args[0]));
            worker.startBackgroundTest();


        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
