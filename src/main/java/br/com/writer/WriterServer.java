package br.com.writer;

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
import br.com.core.network.HttpParser;
import br.com.core.network.TcpStrategy;
import br.com.core.network.UdpStrategy;

public class WriterServer {
    
    public static void main(String[] args) {
        
        try  {

            NodeInfo writerNode = new NodeInfo(UUID.randomUUID(), InetAddress.getLocalHost().getHostAddress(), Integer.parseInt(args[0]), 0);
            NodeInfo readerNode = new NodeInfo(UUID.randomUUID(), "127.0.0.1", 9002, 0);

            MembershipList membershipList = new MembershipList(writerNode);
            membershipList.updateNode(readerNode);

            DictionaryStorage dictionary = new DictionaryStorage();
            
            WriterRequestHandler writeHandler = new WriterRequestHandler(dictionary, membershipList);

            CommunicationStrategy strategy;

            if (args[1].equalsIgnoreCase("UDP")) {
                UdpStrategy udp = new UdpStrategy(writeHandler);
                strategy = udp;

            } else if (args[1].equalsIgnoreCase("TCP")) {
                HttpParser httpParser = new HttpParser();
                TcpStrategy tcp = new TcpStrategy(writeHandler, httpParser);
                strategy = tcp;

            } else if (args[1].equalsIgnoreCase("GRPC")) {
                GrpcMapper grpcMapper = new GrpcMapper();
                GrpcStrategy grpc = new GrpcStrategy(writeHandler, grpcMapper);
                strategy = grpc;

            } else {
                System.out.println("Método inválido.");
                return;
            }

            GossipWorker worker = new GossipWorker(membershipList, strategy, readerNode, Executors.newSingleThreadScheduledExecutor());

            writeHandler.setGossipWorker(worker);
            writeHandler.setLocalNode(readerNode);
            
            System.out.println("Uma instância do Writer acaba de subir na porta " + args[0] + "!");
            strategy.startListening(Integer.parseInt(args[0]));
            worker.startBackgroundTest();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
