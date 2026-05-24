package br.com.core.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.RequestHandler;
import br.com.middleware.network.AbstractTcpServer;

public class TcpStrategy extends AbstractTcpServer implements CommunicationStrategy {

    private final RequestHandler handler;
    private final HttpParser httpParser;

    public TcpStrategy(RequestHandler handler, HttpParser httpParser) {
        this.handler    = handler;
        this.httpParser = httpParser;
    }

    @Override
    public void startListening(int port) {
        listen(port);
    }

    @Override
    protected void handleConnection(Socket connection) {
        try (InputStream is  = connection.getInputStream();
             OutputStream os = connection.getOutputStream()) {

            PushbackInputStream pbis = new PushbackInputStream(is, 1);
            int firstByteInt = pbis.read();
            if (firstByteInt == -1) return;

            byte magicByte = (byte) firstByteInt;

            if (magicByte == (byte) -84) {
                pbis.unread(firstByteInt);
                ObjectOutputStream output = new ObjectOutputStream(os);
                output.flush();
                ObjectInputStream input = new ObjectInputStream(pbis);
                Object received = input.readObject();

                if (received instanceof AppRequest) {
                    AppResponse response = handler.handleRequest((AppRequest) received);
                    output.writeObject(response);
                    output.flush();
                } else if (received instanceof GossipMessage) {
                    handler.handleGossip((GossipMessage) received);
                }

            } else {
                pbis.unread(firstByteInt);

                String requestLine = readLine(pbis);
                if (requestLine == null || requestLine.isEmpty()) return;

                HttpRequestParts parts  = parseRequestLine(requestLine);
                int contentLength       = readHeaders(pbis);
                Map<String, String> params = parseQuery(parts.query);

                if (contentLength > 0) {
                    byte[] bodyBytes = pbis.readNBytes(contentLength);
                    params.putAll(parseQuery(
                        new String(bodyBytes, StandardCharsets.UTF_8)));
                }

                AppRequest request = httpParser.requestConvertor(
                    parts.httpMethod + " /" + parts.objectName
                    + "/" + parts.methodPath
                    + (parts.query.isEmpty() ? "" : "?" + parts.query)
                    + " HTTP/1.1\r\n\r\n");
                AppResponse response  = handler.handleRequest(request);
                String responseString = httpParser.responseGenerator(response);

                os.write(responseString.getBytes(StandardCharsets.UTF_8));
                os.flush();
                connection.shutdownOutput();
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
}