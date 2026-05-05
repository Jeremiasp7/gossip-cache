package br.com.core.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;

public class HttpStrategy implements CommunicationStrategy {

    private RequestHandler handler;

    private HttpParser httpParser;

    public HttpStrategy(RequestHandler handler, HttpParser httpParser) {
        this.handler = handler;
        this.httpParser = httpParser;
    }
    
    @Override
    public void startListening(int port) { // http protocol for listening requests

        ExecutorService executor = Executors.newFixedThreadPool(16);

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Servidor HTTP escutando na porta: " + port);

            while (true) {
                try {
                    Socket connection = server.accept(); // seems like tcp, but works through strings instead of objects
                    executor.submit(() -> { 
                        try { // converts strings in bytes for get the request and write the response
                            InputStream rawInputFlow = connection.getInputStream();
                            byte[] inputBytes = new byte[8192];
                            int bytesQuantity = rawInputFlow.read(inputBytes);

                            if (bytesQuantity > 0) {
                                String inputString = new String(inputBytes, 0, bytesQuantity);
                                AppRequest request = httpParser.requestConvertor(inputString);
                                AppResponse response = handler.handleRequest(request);
                                String responseString = httpParser.responseGenerator(response);
                                
                                OutputStream rawOutputFlow = connection.getOutputStream();
                                byte[] outputBytes = responseString.getBytes(StandardCharsets.UTF_8);
                                rawOutputFlow.write(outputBytes);
                                rawOutputFlow.flush();
                            } else {
                                System.out.println("Conexão encerrada de forma abrupta!");
                            }

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
        } catch (Exception e) {
            e.printStackTrace();
        }
    } 
    
    @Override
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) {

        try (Socket socket = new Socket(destinationNode.getAddress(), destinationNode.getPort())) {

            socket.setSoTimeout(5000); 

            String requestString = httpParser.requestGenerator(request);
            byte[] arrayBytes = requestString.getBytes(StandardCharsets.UTF_8);

            OutputStream output = socket.getOutputStream();
            output.write(arrayBytes);
            output.flush();

            InputStream rawInputFlow = socket.getInputStream();
            byte[] inputBytes = new byte[8192];
            int bytesQuantity = rawInputFlow.read(inputBytes);

            if (bytesQuantity > 0) {

                String inputString = new String(inputBytes, 0, bytesQuantity);
                AppResponse response = httpParser.responseConvertor(inputString);

                return response;
            } else {
                return new AppResponse("503", null, "Server Unavailable");
            }
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            return new AppResponse("503", null, "Service Unavailable / Timeout");

        } catch (Exception e) {
            e.printStackTrace();
            return new AppResponse("500", null, "Internal Server Error");
        }
    } 

    @Override
    public void sendGossip(GossipMessage message, NodeInfo destinationNode) {

        try (Socket socket = new Socket(destinationNode.getAddress(), destinationNode.getPort())) {
            
            String gossipString = httpParser.gossipGenerator(message);
            byte[] gossipBytes = gossipString.getBytes(StandardCharsets.UTF_8);

            OutputStream output = socket.getOutputStream();
            output.write(gossipBytes);
            output.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    } 

}
