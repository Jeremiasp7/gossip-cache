package network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import model.AppRequest;
import model.AppResponse;
import model.GossipMessage;
import model.NodeInfo;
import model.RequestHandler;

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
                
                byte[] inputBytes = new byte[4096];
                DatagramPacket inputPacket = new DatagramPacket(inputBytes, inputBytes.length);

                datagramSocket.receive(inputPacket);

                executor.submit(() -> {
                    try {
                        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(inputPacket.getData());
                        ObjectInputStream input = new ObjectInputStream(byteInputStream);

                        AppRequest request = (AppRequest) input.readObject();
                        AppResponse response = handler.handleRequest(request);

                        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                        ObjectOutputStream output = new ObjectOutputStream(byteOutputStream);
                        output.writeObject(response);
                        output.flush();

                        byte[] outputBytes = byteOutputStream.toByteArray();

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
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) {

        try (DatagramSocket socket = new DatagramSocket()) {

            socket.setSoTimeout(5000); 

            // send the request
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

            // receive the response
            byte[] inputBytes = new byte[4096];
            DatagramPacket inputPacket = new DatagramPacket(inputBytes, inputBytes.length);

            socket.receive(inputPacket);

            ByteArrayInputStream byteInputStream = new ByteArrayInputStream(inputPacket.getData(), 
                0, inputPacket.getLength()
            );
            ObjectInputStream input = new ObjectInputStream(byteInputStream);

            AppResponse response = (AppResponse) input.readObject();

            return response;

        } catch (java.net.SocketTimeoutException timeoutException) {
            System.err.println("Tempo esgotado aguardando resposta de " + destinationNode.getPort());
            return new AppResponse("503", null, "Service Unavailable / Timeout");

        } catch (Exception e) {
            e.printStackTrace();
            return new AppResponse("500", null, "Internal Server Error");
        }
    }

    @Override
    public void sendGossip(GossipMessage message, NodeInfo destinationNode) {

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
