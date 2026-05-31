package br.com.core.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;
import br.com.middleware.network.AbstractTcpServer;

public class TcpStrategy extends AbstractTcpServer implements CommunicationStrategy {

    private final RequestHandler handler;

    private final ExecutorService gossipExecutor = Executors.newFixedThreadPool(16);

    private static final int MAX_CONCURRENT_PER_PEER = 8;
    private final ConcurrentHashMap<UUID, java.util.concurrent.Semaphore> peerSemaphores =
            new ConcurrentHashMap<>();


    public void evictPool(UUID nodeId) {
        peerSemaphores.remove(nodeId);
    }

    private java.util.concurrent.Semaphore semaphoreFor(NodeInfo node) {
        return peerSemaphores.computeIfAbsent(
                node.getSequenceNumber(),
                k -> new java.util.concurrent.Semaphore(MAX_CONCURRENT_PER_PEER, true));
    }

    private Socket openSocket(NodeInfo node) throws IOException {
        Socket socket = new Socket();
        socket.setReuseAddress(true);
        socket.setKeepAlive(false);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(5000);
        socket.connect(
                new InetSocketAddress(node.getAddress(), node.getPort()), 3000);
        return socket;
    }

    public TcpStrategy(RequestHandler handler) {
        this.handler = handler;
    }

    @Override
    public void startListening(int port) {
        listen(port);
    }

    @Override
    protected void onAccepted(Socket connection) {
        try {
            PushbackInputStream pbis =
                    new PushbackInputStream(connection.getInputStream(), 1);
            int firstByteInt = pbis.read();
            if (firstByteInt == -1) { connection.close(); return; }

            byte magicByte = (byte) firstByteInt;
            pbis.unread(firstByteInt);

            SocketWithPushback wrapped = new SocketWithPushback(connection, pbis);

            if (magicByte == (byte) -84) {
                gossipExecutor.submit(() -> handleSerialized(wrapped));
            } else {
                System.err.println("[TcpStrategy] Tráfego não-serializado recebido na porta de cluster. Ignorando.");
                try { connection.close(); } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            try { connection.close(); } catch (IOException ignored) {}
        }
    }

    private void handleSerialized(Socket connection) {
        try {
            OutputStream os = connection.getOutputStream();
            InputStream  is = connection.getInputStream();

            ObjectOutputStream output = new ObjectOutputStream(os);
            output.flush();
            ObjectInputStream input = new ObjectInputStream(is);

            Object received = input.readObject();

            if (received instanceof AppRequest) {
                AppRequest appRequest = (AppRequest) received;
                AppResponse response = handler.handleRequest(appRequest);
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

    @Override
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) {
        int maxRetries = 3;
        int delayMs    = 200;

        Semaphore sem = semaphoreFor(destinationNode);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            boolean acquired = false;
            try {
                acquired = sem.tryAcquire(2000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            if (!acquired) {
                System.err.println("[TcpStrategy] Semáforo cheio para porta "
                        + destinationNode.getPort() + " (tentativa " + attempt + ")");
                continue;
            }

            try (Socket socket = openSocket(destinationNode)) {
                ObjectOutputStream output =
                        new ObjectOutputStream(socket.getOutputStream());
                output.flush();
                ObjectInputStream input =
                        new ObjectInputStream(socket.getInputStream());

                output.writeObject(request);
                output.flush();

                return (AppResponse) input.readObject();

            } catch (java.net.ConnectException e) {
                System.err.println("[TcpStrategy] Tentativa " + attempt
                        + "/" + maxRetries + " falhou para porta "
                        + destinationNode.getPort() + ": " + e.getMessage());
                if (attempt < maxRetries) {
                    try { Thread.sleep((long) delayMs * attempt); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("[TcpStrategy] Erro na tentativa " + attempt
                        + " para porta " + destinationNode.getPort()
                        + ": " + e.getMessage());
                if (attempt < maxRetries) {
                    try { Thread.sleep((long) delayMs * attempt); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                sem.release();
            }
        }

        return new AppResponse("500", null, "Erro Interno de Comunicação no Cluster");
    }

    @Override
    public void sendGossip(GossipMessage message, NodeInfo destinationNode) {
        try (Socket socket = openSocket(destinationNode)) {
            ObjectOutputStream output =
                    new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            output.writeObject(message);
            output.flush();
        } catch (Exception e) {
            System.err.println("[TcpStrategy] Erro gossip para porta "
                    + destinationNode.getPort() + ": " + e.getMessage());
        }
    }
}
