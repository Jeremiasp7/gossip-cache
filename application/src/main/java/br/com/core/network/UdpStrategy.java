package br.com.core.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;

public class UdpStrategy implements CommunicationStrategy {
    
    private RequestHandler handler;

    public UdpStrategy(RequestHandler handler) {
        this.handler = handler;
    }

    @Override
    public void startListening(int port) { 

        ExecutorService executor = Executors.newFixedThreadPool(16);

        try (DatagramSocket datagramSocket = new DatagramSocket(port)) { 
            System.out.println("Servidor UDP escutando na porta: " + port);

            while (true) {
                
                byte[] inputBytes = new byte[8192]; 
                DatagramPacket inputPacket = new DatagramPacket(inputBytes, inputBytes.length);

                datagramSocket.receive(inputPacket);

                executor.submit(() -> {
                    try { 
                        byte[] inputData = inputPacket.getData();
                        int offset = inputPacket.getOffset();
                        int length = inputPacket.getLength();

                        byte magicByte = inputData[offset];
                        AppRequest request = null;
                        Object receivedObject = null;
                        
                        boolean isJMeterText = false; 
                        boolean isGossip = false;
                        if (magicByte == (byte) -84) { // any serialized object starts with byte -84 -> gateway or writer
                            ByteArrayInputStream byteInputStream = new ByteArrayInputStream(inputData, offset, length);
                            ObjectInputStream input = new ObjectInputStream(byteInputStream);
                            
                            receivedObject = input.readObject();

                            if (receivedObject instanceof AppRequest) {
                                request = (AppRequest) receivedObject;
                            } else if (receivedObject instanceof GossipMessage) {
                                isGossip = true;
                            }
                            
                        } else { // jmeter send requests
                            isJMeterText = true;
                            String text = new String(inputData, offset, length).trim();
                            String[] parts = text.split(","); 
                            
                            br.com.core.model.Operation op = br.com.core.model.Operation.valueOf(parts[0].trim());
                            String key = parts.length > 1 ? parts[1].trim() : null;
                            byte[] value = parts.length > 2 ? parts[2].trim().getBytes() : null;
                            
                            request = new AppRequest(op, key, value);
                        }

                        if (isGossip) {
                            GossipMessage gossip = (GossipMessage) receivedObject;
                            handler.handleGossip(gossip);
                            return; 
                        }

                        AppResponse response = handler.handleRequest(request);

                        byte[] outputBytes;
                        
                        if (isJMeterText) { // form a response from the jmeter request
                            String responseText = response.getStatus() + " - " + response.getMessage();
                            outputBytes = responseText.getBytes();
                        } else { // work with the binary response that comes from gateway
                            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                            ObjectOutputStream output = new ObjectOutputStream(byteOutputStream);
                            output.writeObject(response);
                            output.flush();
                            outputBytes = byteOutputStream.toByteArray();
                        }

                        DatagramPacket outputPacket = new DatagramPacket(outputBytes, outputBytes.length, 
                            inputPacket.getAddress(), inputPacket.getPort()
                        );
                        datagramSocket.send(outputPacket);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    } 

    @Override
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) { // gateway use this method for ask data to the writer or reader

        try (DatagramSocket socket = new DatagramSocket()) {

            socket.setSoTimeout(5000); 

            ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
            ObjectOutputStream objectOutput = new ObjectOutputStream(byteOutput);

            objectOutput.writeObject(request);
            objectOutput.flush();
            byte[] sendBytes = byteOutput.toByteArray();

            InetAddress destinationNodeAdress = InetAddress.getByName(destinationNode.getAddress());
            DatagramPacket outputPacket = new DatagramPacket(sendBytes, sendBytes.length, 
                destinationNodeAdress, destinationNode.getPort()
            );

            socket.send(outputPacket);

            byte[] inputBytes = new byte[8192];
            DatagramPacket inputPacket = new DatagramPacket(inputBytes, inputBytes.length);

            socket.receive(inputPacket);

            ByteArrayInputStream byteInputStream = new ByteArrayInputStream(inputPacket.getData(), 
                0, inputPacket.getLength()
            );
            ObjectInputStream input = new ObjectInputStream(byteInputStream);

            AppResponse response = (AppResponse) input.readObject();

            return response;

        } catch (SocketTimeoutException timeoutException) {
            System.err.println("Tempo esgotado aguardando resposta de " + destinationNode.getPort());
            return new AppResponse("503", null, "Service Unavailable / Timeout");

        } catch (Exception e) {
            e.printStackTrace();
            return new AppResponse("500", null, "Internal Server Error");
        }
    }

    @Override
    public void sendGossip(GossipMessage message, NodeInfo destinationNode) { // the writer serializes the gossip message and send to the reader

        try (DatagramSocket socket = new DatagramSocket()) {

            InetAddress destinationNodeAddress = InetAddress.getByName(destinationNode.getAddress());
            
            ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
            ObjectOutputStream output = new ObjectOutputStream(byteOutput);

            output.writeObject(message);
            output.flush();
            byte[] sendBytes = byteOutput.toByteArray();

            DatagramPacket gossipPacket = new DatagramPacket(sendBytes, sendBytes.length, 
                destinationNodeAddress, destinationNode.getPort()
            );

            socket.send(gossipPacket);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
