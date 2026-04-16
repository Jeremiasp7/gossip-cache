package br.com.core.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;

public class TcpStrategy implements CommunicationStrategy {
    
    private RequestHandler handler;
    private HttpParser httpParser;

    public TcpStrategy(RequestHandler handler, HttpParser httpParser) {
        this.handler = handler;
        this.httpParser = httpParser;
    }

    @Override
    public void startListening(int port){ 
        
        ExecutorService executor = Executors.newFixedThreadPool(16);

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Servidor TCP/HTTP escutando na porta: " + port);

            while (true) {
                try {
                    Socket connection = server.accept();
                    executor.submit(() -> { 
                        try (InputStream is = connection.getInputStream();
                             OutputStream os = connection.getOutputStream()) {
                            
                            PushbackInputStream pbis = new PushbackInputStream(is, 1); // spy the first byte of the packet
                            int firstByteInt = pbis.read();
                            
                            if (firstByteInt != -1) {
                                byte magicByte = (byte) firstByteInt;

                                if (magicByte == (byte) -84) { // any serialized object starts with byte -84 -> gateway or writer
                                    pbis.unread(firstByteInt);
                                    
                                    ObjectOutputStream output = new ObjectOutputStream(os);
                                    output.flush();

                                    ObjectInputStream input = new ObjectInputStream(pbis);
                                    
                                    Object receivedObject = input.readObject();

                                    if (receivedObject instanceof AppRequest) {
                                        AppRequest request = (AppRequest) receivedObject;
                                        AppResponse response = handler.handleRequest(request);
                                        
                                        output.writeObject(response);
                                        output.flush(); 
                                        
                                    } else if (receivedObject instanceof GossipMessage) {
                                        GossipMessage gossip = (GossipMessage) receivedObject;
                                        handler.handleGossip(gossip);
                                    }
                                    
                                } else { // jmeter send requests, uses the http parser for threat the request
                                    pbis.unread(firstByteInt); 
                                    
                                    byte[] inputBytes = new byte[8192];
                                    int bytesQuantity = pbis.read(inputBytes);

                                    if (bytesQuantity > 0) {
                                        String inputString = new String(inputBytes, 0, bytesQuantity);
                                        
                                        try {
                                            AppRequest request = httpParser.requestConvertor(inputString);
                                            AppResponse response = handler.handleRequest(request);
                                            String responseString = httpParser.responseGenerator(response);
                                            
                                            byte[] outputBytes = responseString.getBytes(StandardCharsets.UTF_8);
                                            os.write(outputBytes);
                                            os.flush(); 

                                            connection.shutdownOutput(); 
                                            Thread.sleep(10); 
                                            while (is.available() > 0) { // cclose the connection in a clean way
                                                is.read();
                                            }

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
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
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    @Override
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) { // gateway use this method for ask data to the writer or reader
        try (Socket socket = new Socket(destinationNode.getAddress(), destinationNode.getPort())) {
            socket.setSoTimeout(5000); 

            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();

            ObjectInputStream input = new ObjectInputStream(socket.getInputStream());

            output.writeObject(request);
            output.flush();

            return (AppResponse) input.readObject();

        } catch (Exception e) {
            return new AppResponse("500", null, "Erro Interno de Comunicação no Cluster");
        }
    }

    @Override   
    public void sendGossip(GossipMessage message, NodeInfo destinationNode) { 
        try (Socket socket = new Socket(destinationNode.getAddress(), destinationNode.getPort())) {
            
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();

            //ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
            
            output.writeObject(message);
            output.flush(); 
            
        } catch (Exception e) {
            System.err.println("Erro na Fofoca TCP: " + e.getMessage());
            e.printStackTrace();
        }
    }
}