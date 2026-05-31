package br.com.middleware.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MiddlewareServer {

    private final int port;
    private final ProtocolPlugin protocolPlugin;
    protected final ExecutorService executor = Executors.newFixedThreadPool(32);

    public MiddlewareServer(int port, ProtocolPlugin protocolPlugin) {
        this.port = port;
        this.protocolPlugin = protocolPlugin;
    }

    public void start() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port, 200)) {
                System.out.println("[MiddlewareServer] Escutando na porta " + port);
                while (true) {
                    Socket connection = server.accept();
                    executor.submit(() -> handleConnection(connection));
                }
            } catch (IOException e) {
                System.err.println("[MiddlewareServer] Erro fatal: " + e.getMessage());
            }
        }, "MiddlewareServer-acceptor").start();
    }

    private void handleConnection(Socket connection) {
        try {
            InputStream is = connection.getInputStream();
            byte[] firstByte = new byte[1];
            int read = is.read(firstByte);

            if (read == -1) {
                connection.close();
                return;
            }

            byte magicByte = firstByte[0];

            if (magicByte == (byte) -84) {
                System.err.println("[MiddlewareServer] Tráfego serializado desviado (não é HTTP)");
                connection.close();
            } else {
                Socket wrappedConnection = wrapWithPushback(connection, firstByte[0], is);
                protocolPlugin.handleHttpConnection(wrappedConnection);
            }
        } catch (IOException e) {
            try { connection.close(); } catch (IOException ignored) {}
        }
    }

    private Socket wrapWithPushback(Socket connection, byte firstByte, InputStream originalIs) throws IOException {
        PushbackInputStream pbis = new PushbackInputStream(originalIs, 1);
        pbis.unread(firstByte);

        return new Socket() {
            @Override
            public InputStream getInputStream() throws IOException {
                return pbis;
            }

            @Override
            public java.io.OutputStream getOutputStream() throws IOException {
                return connection.getOutputStream();
            }

            @Override
            public void close() throws IOException {
                connection.close();
            }

            @Override
            public void setSoTimeout(int timeout) throws java.net.SocketException {
                connection.setSoTimeout(timeout);
            }
        };
    }
}
