package network;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import model.AppRequest;
import model.AppResponse;
import model.GossipMessage;
import model.NodeInfo;
import model.RequestHandler;

public class TcpStrategy implements CommunicationStrategy {
    
    private RequestHandler handler;

    public TcpStrategy(RequestHandler handler) {
        this.handler = handler;
    }

    @Override
    public void startListening(int port){
        
        ExecutorService executor = Executors.newFixedThreadPool(16);

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Servidor TCP escutando na porta: " + port);

            while (true) {
                try {
                    Socket connection = server.accept();
                    executor.submit(() -> {
                        try {
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
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) {

        AppResponse response = new AppResponse();

        return response;
    }

    @Override   
    public void sendGossip(GossipMessage message, NodeInfo destinationNode) {

    }
}
