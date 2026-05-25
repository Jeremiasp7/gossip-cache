package br.com.core.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;
import br.com.middleware.network.AbstractTcpServer;
import br.com.middleware.network.ProtocolPlugin;

public class TcpStrategy extends AbstractTcpServer implements CommunicationStrategy {

    private final RequestHandler handler;
    private final HttpParser httpParser;
    private ProtocolPlugin plugin; // injetado pelo servidor
    private final ExecutorService gossipExecutor = Executors.newFixedThreadPool(16);

    public TcpStrategy(RequestHandler handler, HttpParser httpParser) {
        this.handler    = handler;
        this.httpParser = httpParser;
    }

    // Chamado pelo servidor após Broker.build()
    public void setPlugin(ProtocolPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void startListening(int port) {
        listen(port);
    }

    @Override
    protected void handleConnection(Socket connection) {
        // Detecta o tipo ANTES de submeter ao executor
        try {
            InputStream is = connection.getInputStream();
            PushbackInputStream pbis = new PushbackInputStream(is, 1);
            int firstByteInt = pbis.read();
            if (firstByteInt == -1) { connection.close(); return; }

            byte magicByte = (byte) firstByteInt;
            pbis.unread(firstByteInt);

            if (magicByte == (byte) -84) {
                // Gossip/AppRequest — usa pool dedicado, nunca fica atrás do HTTP
                gossipExecutor.submit(() -> handleSerialized(
                    new SocketWithPushback(connection, pbis)));
            } else {
                // HTTP — usa o executor herdado do AbstractTcpServer
                if (plugin != null) {
                    plugin.handleHttpConnection(
                        new SocketWithPushback(connection, pbis));
                } else {
                    handleHttpFallback(connection, pbis);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            try { connection.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) {
        try (Socket socket = new Socket(
                destinationNode.getAddress(), destinationNode.getPort())) {
            socket.setSoTimeout(5000);
            ObjectOutputStream output =
                new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            ObjectInputStream input =
                new ObjectInputStream(socket.getInputStream());
            output.writeObject(request);
            output.flush();
            return (AppResponse) input.readObject();
        } catch (Exception e) {
            return new AppResponse("500", null, "Erro Interno de Comunicação no Cluster");
        }
    }

    @Override
    public void sendGossip(GossipMessage message, NodeInfo destinationNode) {
        try (Socket socket = new Socket(
                destinationNode.getAddress(), destinationNode.getPort())) {
            ObjectOutputStream output =
                new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            output.writeObject(message);
            output.flush();
        } catch (Exception e) {
            System.err.println("Erro na Fofoca TCP: " + e.getMessage());
        }
    }

    private void handleSerialized(Socket connection) {
        try (InputStream is  = connection.getInputStream();
            OutputStream os = connection.getOutputStream()) {

            ObjectOutputStream output = new ObjectOutputStream(os);
            output.flush();
            ObjectInputStream input   = new ObjectInputStream(is);
            Object received           = input.readObject();

            if (received instanceof AppRequest) {
                AppResponse response = handler.handleRequest((AppRequest) received);
                output.writeObject(response);
                output.flush();
            } else if (received instanceof GossipMessage) {
                handler.handleGossip((GossipMessage) received);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { connection.close(); } catch (IOException ignored) {}
        }
    }

    private void handleHttpFallback(Socket connection, PushbackInputStream pbis) {
        try (OutputStream os = connection.getOutputStream()) {
            byte[] inputBytes = new byte[8192];
            int bytesQuantity = pbis.read(inputBytes);
            if (bytesQuantity > 0) {
                String inputString    = new String(inputBytes, 0, bytesQuantity);
                AppRequest request    = httpParser.requestConvertor(inputString);
                AppResponse response  = handler.handleRequest(request);
                String responseString = httpParser.responseGenerator(response);
                os.write(responseString.getBytes());
                os.flush();
                connection.shutdownOutput();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { connection.close(); } catch (IOException ignored) {}
        }
    }
}