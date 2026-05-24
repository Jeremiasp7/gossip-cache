package br.com.core.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;
import br.com.middleware.network.AbstractUdpServer;

public class UdpStrategy extends AbstractUdpServer implements CommunicationStrategy {

    private final RequestHandler handler;

    public UdpStrategy(RequestHandler handler) {
        this.handler = handler;
    }

    @Override
    public void startListening(int port) {
        listen(port);
    }

    @Override
    protected void handlePacket(byte[] data, int offset, int length,
                                InetAddress addr, int port,
                                DatagramSocket socket) {
        try {
            byte magicByte = data[offset];

            if (magicByte == (byte) -84) {
                ByteArrayInputStream bis = new ByteArrayInputStream(data, offset, length);
                ObjectInputStream input  = new ObjectInputStream(bis);
                Object received          = input.readObject();

                if (received instanceof AppRequest) {
                    AppResponse response = handler.handleRequest((AppRequest) received);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream output = new ObjectOutputStream(bos);
                    output.writeObject(response);
                    output.flush();
                    sendResponse(socket, bos.toByteArray(), addr, port);

                } else if (received instanceof GossipMessage) {
                    handler.handleGossip((GossipMessage) received);
                }

            } else {
                String text   = new String(data, offset, length).trim();
                String[] parts = text.split(",");

                br.com.core.model.Operation op =
                    br.com.core.model.Operation.valueOf(parts[0].trim());
                String key   = parts.length > 1 ? parts[1].trim() : null;
                byte[] value = parts.length > 2 ? parts[2].trim().getBytes() : null;

                AppResponse response = handler.handleRequest(
                    new AppRequest(op, key, value));
                String responseText  = response.getStatus() + " - " + response.getMessage();
                sendResponse(socket, responseText.getBytes(), addr, port);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream output = new ObjectOutputStream(bos);
            output.writeObject(request);
            output.flush();
            byte[] sendBytes = bos.toByteArray();
            InetAddress addr = InetAddress.getByName(destinationNode.getAddress());
            socket.send(new DatagramPacket(
                sendBytes, sendBytes.length, addr, destinationNode.getPort()));
            byte[] inputBytes = new byte[8192];
            DatagramPacket inputPacket = new DatagramPacket(inputBytes, inputBytes.length);
            socket.receive(inputPacket);
            ByteArrayInputStream bis = new ByteArrayInputStream(
                inputPacket.getData(), 0, inputPacket.getLength());
            return (AppResponse) new ObjectInputStream(bis).readObject();
        } catch (SocketTimeoutException e) {
            return new AppResponse("503", null, "Service Unavailable / Timeout");
        } catch (Exception e) {
            return new AppResponse("500", null, "Internal Server Error");
        }
    }

    @Override
    public void sendGossip(GossipMessage message, NodeInfo destinationNode) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress addr = InetAddress.getByName(destinationNode.getAddress());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream output = new ObjectOutputStream(bos);
            output.writeObject(message);
            output.flush();
            byte[] bytes = bos.toByteArray();
            socket.send(new DatagramPacket(
                bytes, bytes.length, addr, destinationNode.getPort()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}