package br.com.core.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.Operation;
import br.com.core.model.RequestHandler;
import br.com.middleware.network.AbstractTcpServer;
import br.com.middleware.network.ProtocolPlugin;

public class TcpStrategy extends AbstractTcpServer implements CommunicationStrategy {

    private final RequestHandler handler;
    private final HttpParser httpParser;
    private ProtocolPlugin plugin;

    private final ExecutorService gossipExecutor = Executors.newFixedThreadPool(16);

    // -------------------------------------------------------------------------
    // Sem pool de conexões — uma conexão por request.
    //
    // HISTÓRICO DAS TENTATIVAS:
    //
    //   v1 (original): uma conexão por request, sem SO_REUSEADDR.
    //      Problema: TIME_WAIT esgotava as ~28k portas efêmeras em ~25s
    //      sob carga do JMeter.
    //
    //   v2 (pool de Socket): guardava o Socket e recriava ObjectOutputStream /
    //      ObjectInputStream a cada uso.
    //      Problema: ObjectOutputStream escreve header de stream (AC ED 00 05)
    //      a cada instanciação. O ObjectInputStream do servidor esperava esse
    //      header apenas uma vez; na segunda requisição recebia o header como
    //      objeto, lançava StreamCorruptedException → 50% de erros 500.
    //
    //   v3 (pool de PooledConnection): guardava socket + OOS + OIS juntos.
    //      Problema: o servidor fecha a conexão aceita no finally de
    //      handleSerialized() após cada request. socket.isConnected() e
    //      socket.isClosed() refletem apenas o estado LOCAL do socket (JVM),
    //      não o estado real da conexão TCP. O socket parecia vivo no pool
    //      mas estava morto no SO → SocketException "conexão anulada" → 10%
    //      de erros 500.
    //
    //   v4 (atual): volta a uma conexão por request, mas com SO_REUSEADDR=true
    //      no socket cliente. SO_REUSEADDR permite que uma nova conexão reuse
    //      um par (endereço local, porta local) ainda em TIME_WAIT, eliminando
    //      o esgotamento de portas sem os problemas de estado dos pools.
    //      Um semáforo por peer limita a concorrência máxima de saída, evitando
    //      abertura explosiva de conexões sob pico de carga.
    // -------------------------------------------------------------------------

    // Controle de concorrência de saída por peer.
    // Limita quantas conexões simultâneas o Gateway abre para o mesmo Reader/Writer.
    // Sem esse limite, sob pico do JMeter todos os threads tentariam conectar ao
    // mesmo nó ao mesmo tempo, degradando o throughput.
    private static final int MAX_CONCURRENT_PER_PEER = 8;
    private final ConcurrentHashMap<UUID, java.util.concurrent.Semaphore> peerSemaphores =
            new ConcurrentHashMap<>();

    // Rastreia UUIDs para o evictPool (chamado por MembershipList ao remover nó).
    // Sem pool de sockets, basta remover o semáforo.
    public void evictPool(UUID nodeId) {
        peerSemaphores.remove(nodeId);
    }

    private java.util.concurrent.Semaphore semaphoreFor(NodeInfo node) {
        return peerSemaphores.computeIfAbsent(
                node.getSequenceNumber(),
                k -> new java.util.concurrent.Semaphore(MAX_CONCURRENT_PER_PEER, true));
    }

    /** Abre um socket novo com SO_REUSEADDR para evitar TIME_WAIT. */
    private Socket openSocket(NodeInfo node) throws IOException {
        Socket socket = new Socket();
        socket.setReuseAddress(true);   // ← permite reuso de porta em TIME_WAIT
        socket.setKeepAlive(false);     // conexão curta — keepalive não ajuda
        socket.setTcpNoDelay(true);     // sem buffer de Nagle — reduz latência
        socket.setSoTimeout(5000);
        socket.connect(
                new InetSocketAddress(node.getAddress(), node.getPort()), 3000);
        return socket;
    }

    public TcpStrategy(RequestHandler handler, HttpParser httpParser) {
        this.handler    = handler;
        this.httpParser = httpParser;
    }

    public void setPlugin(ProtocolPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Servidor — lado receptor (sem alteração de comportamento)
    // -------------------------------------------------------------------------

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
                executor.submit(() -> handleHttp(wrapped));
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

                if (plugin != null) {
                    String httpMethod = operationToHttpMethod(appRequest.getOperation());
                    String methodPath = operationToPath(appRequest.getOperation());

                    Map<String, String> params = new java.util.LinkedHashMap<>();
                    if (appRequest.getKey() != null)
                        params.put("key", appRequest.getKey());
                    if (appRequest.getValue() != null)
                        params.put("value", new String(
                                appRequest.getValue(),
                                java.nio.charset.StandardCharsets.UTF_8));

                    br.com.middleware.dto.InvocationRequest invocationRequest =
                            plugin.getMarshaller().unmarshal(
                                    httpMethod, "dictionary", methodPath, params);

                    String resultBody =
                            plugin.getServerRequestHandler().handle(invocationRequest);

                    AppResponse response;
                    if (resultBody.contains("\"error\"")) {
                        response = new AppResponse("500", null, resultBody);
                    } else {
                        byte[] resultBytes =
                                resultBody != null && !resultBody.equals("null")
                                        ? resultBody.getBytes(
                                                java.nio.charset.StandardCharsets.UTF_8)
                                        : null;
                        response = new AppResponse("200", resultBytes, "OK");
                    }
                    output.writeObject(response);
                    output.flush();
                }

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
        plugin.handleHttpConnection(connection);
    }

    // -------------------------------------------------------------------------
    // Cliente: sendRequest — uma conexão por request, SO_REUSEADDR + semáforo
    // -------------------------------------------------------------------------

    @Override
    public AppResponse sendRequest(AppRequest request, NodeInfo destinationNode) {
        int maxRetries = 3;
        int delayMs    = 200;

        java.util.concurrent.Semaphore sem = semaphoreFor(destinationNode);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // Adquire permissão — bloqueia se já houver MAX_CONCURRENT_PER_PEER
            // conexões abertas para este peer. Timeout de 2s para não segurar
            // a thread do JMeter indefinidamente.
            boolean acquired = false;
            try {
                acquired = sem.tryAcquire(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
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

    // -------------------------------------------------------------------------
    // Cliente: sendGossip — uma conexão por envio, SO_REUSEADDR
    // Gossip é fire-and-forget: não usa semáforo (não bloqueia o scheduler)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String operationToPath(Operation operation) {
        switch (operation) {
            case GET:    return "get";
            case POST:
            case PUT:    return "post";
            case DELETE: return "delete";
            default: throw new RuntimeException("Operação não mapeada: " + operation);
        }
    }

    private String operationToHttpMethod(Operation operation) {
        switch (operation) {
            case GET:    return "GET";
            case POST:
            case PUT:    return "POST";
            case DELETE: return "POST";
            default: throw new RuntimeException("Operação não mapeada: " + operation);
        }
    }
}