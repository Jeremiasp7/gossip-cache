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
    private ProtocolPlugin plugin;

    // Pool dedicado para gossip — nunca bloqueado pelo HTTP
    private final ExecutorService gossipExecutor =
        Executors.newFixedThreadPool(16);

    public TcpStrategy(RequestHandler handler, HttpParser httpParser) {
        this.handler    = handler;
        this.httpParser = httpParser;
    }

    public void setPlugin(ProtocolPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void startListening(int port) {
        listen(port);
    }

    // Roda no thread do acceptor — lê apenas 1 byte e despacha imediatamente
    // Nunca bloqueia o loop de accept
    @Override
    protected void onAccepted(Socket connection) {
        try {
            PushbackInputStream pbis =
                new PushbackInputStream(connection.getInputStream(), 1);
            int firstByteInt = pbis.read(); // lê 1 byte — muito rápido
            if (firstByteInt == -1) {
                connection.close();
                return;
            }

            byte magicByte = (byte) firstByteInt;
            pbis.unread(firstByteInt);

            SocketWithPushback wrapped = new SocketWithPushback(connection, pbis);

            if (magicByte == (byte) -84) {
                // Gossip/AppRequest — pool dedicado, prioridade garantida
                gossipExecutor.submit(() -> handleSerialized(wrapped));
            } else {
                // HTTP — pool do executor herdado
                executor.submit(() -> handleHttp(wrapped));
            }
        } catch (IOException e) {
            try { connection.close(); } catch (IOException ignored) {}
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
            System.err.println("[TcpStrategy] Erro serializado: " + e.getMessage());
        } finally {
            try { connection.close(); } catch (IOException ignored) {}
        }
    }

    private void handleHttp(Socket connection) {
        if (plugin != null) {
            plugin.handleHttpConnection(connection);
        } else {
            // Fallback sem middleware
            try (InputStream is  = connection.getInputStream();
                 OutputStream os = connection.getOutputStream()) {
                byte[] inputBytes = new byte[8192];
                int bytesQuantity = is.read(inputBytes);
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

    @Override
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) {
        int maxRetries = 3;
        int delayMs    = 100;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
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

            } catch (java.net.ConnectException e) {
                // Nó inacessível — retry com backoff
                System.err.println("[TcpStrategy] Tentativa " + attempt
                    + "/" + maxRetries + " falhou para porta "
                    + destinationNode.getPort() + ": " + e.getMessage());

                if (attempt < maxRetries) {
                    try { Thread.sleep(delayMs * attempt); } // 100ms, 200ms
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                // Erro não transitório — não faz sentido retry
                System.err.println("[TcpStrategy] Erro não transitório: "
                    + e.getMessage());
                break;
            }
        }

        return new AppResponse("500", null, "Erro Interno de Comunicação no Cluster");
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
}