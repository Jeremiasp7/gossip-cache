package br.com.core.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.util.Map;
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
                AppRequest appRequest = (AppRequest) received;

                // Se o plugin está disponível, roteia pelo middleware
                if (plugin != null) {
                    String httpMethod  = operationToHttpMethod(appRequest.getOperation());
                    String methodPath  = operationToPath(appRequest.getOperation());

                    // Monta os parâmetros como o Marshaller espera
                    Map<String, String> params = new java.util.LinkedHashMap<>();
                    if (appRequest.getKey() != null)
                        params.put("key", appRequest.getKey());
                    if (appRequest.getValue() != null)
                        params.put("value", new String(
                            appRequest.getValue(), java.nio.charset.StandardCharsets.UTF_8));

                    br.com.middleware.dto.InvocationRequest invocationRequest =
                        plugin.getMarshaller().unmarshal(
                            httpMethod, "dictionary", methodPath, params);

                    String resultBody =
                        plugin.getServerRequestHandler().handle(invocationRequest);

                    // Converte o resultado de volta para AppResponse
                    AppResponse response;
                    if (resultBody.contains("\"error\"")) {
                        response = new AppResponse("500", null, resultBody);
                    } else {
                        byte[] resultBytes = resultBody != null && !resultBody.equals("null")
                            ? resultBody.getBytes(java.nio.charset.StandardCharsets.UTF_8)
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

    private String operationToPath(Operation operation) {
        switch (operation) {
            case GET:    return "get";
            case POST:
            case PUT:    return "post";
            default: throw new RuntimeException("Operação não mapeada: " + operation);
        }
    }

    private String operationToHttpMethod(Operation operation) {
        switch (operation) {
            case GET:    return "GET";
            case POST:
            case PUT:    return "POST";
            case DELETE: return "POST"; // deleteLocalData é chamado via POST no DictionaryStorage
            default: throw new RuntimeException("Operação não mapeada: " + operation);
        }
    }
}