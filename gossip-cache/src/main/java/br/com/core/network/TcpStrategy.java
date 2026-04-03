package br.com.core.network;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;

public class TcpStrategy implements CommunicationStrategy {
    
    private RequestHandler handler;

    public TcpStrategy(RequestHandler handler) {
        this.handler = handler;
    }

    @Override
    public void startListening(int port){ // tcp protocol for listening requests
        
        ExecutorService executor = Executors.newFixedThreadPool(16);

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Servidor TCP escutando na porta: " + port);

            while (true) {
                try {
                    Socket connection = server.accept();
                    executor.submit(() -> { // execution of the jobs in the threads
                        try { // starts a conection, a message comes via socket and a response is send
                            ObjectOutputStream output = new ObjectOutputStream(connection.getOutputStream());
                            output.flush();

                            ObjectInputStream input = new ObjectInputStream(connection.getInputStream());

                            AppRequest request = (AppRequest) input.readObject();
                            AppResponse response = handler.handleRequest(request);
                            
                            output.writeObject(response);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                if (!connection.isClosed()) connection.close();
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    @Override
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) { // send a udp request and waits a synchronous response
        
        try (Socket socket = new Socket(destinationNode.getAddress(), destinationNode.getPort())) {

            socket.setSoTimeout(5000); 

            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();

            ObjectInputStream input = new ObjectInputStream(socket.getInputStream());

            output.writeObject(request);

            AppResponse response = (AppResponse) input.readObject();

            return response;

        } catch (Exception e) {
            e.printStackTrace();
            AppResponse response = new AppResponse("404", null, "Not Found");
            return response;
        }

    }

    @Override   
    public void sendGossip(GossipMessage message, NodeInfo destinationNode) { // when a node is changed, a gossip is send

        try (Socket socket = new Socket(destinationNode.getAddress(), destinationNode.getPort())) {
            
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();

            output.writeObject(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
