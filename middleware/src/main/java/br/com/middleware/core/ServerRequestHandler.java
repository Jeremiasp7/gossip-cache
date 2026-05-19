package br.com.middleware.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerRequestHandler {

    private int port;
    private Invoker invoker;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean running;

    public ServerRequestHandler(int port, Invoker invoker) {
        this.port = port;
        this.invoker = invoker;
        this.executorService = Executors.newFixedThreadPool(10);
        this.running = false;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("[SRH] Servidor aguardando conexões na porta " + port);

        new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[SRH] Cliente conectado: " + clientSocket.getInetAddress().getHostAddress());

                    executorService.execute(new ClientHandler(clientSocket, invoker));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[SRH] Erro ao aceitar conexão: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            executorService.shutdown();
            System.out.println("[SRH] Servidor parou");
        } catch (IOException e) {
            System.err.println("[SRH] Erro ao parar o servidor: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {

        private Socket clientSocket;
        private Invoker invoker;

        public ClientHandler(Socket clientSocket, Invoker invoker) {
            this.clientSocket = clientSocket;
            this.invoker = invoker;
        }

        @Override
        public void run() {
            try (InputStream input = clientSocket.getInputStream();
                 OutputStream output = clientSocket.getOutputStream()) {

                byte[] sizeBytes = new byte[4];
                int bytesRead = input.read(sizeBytes);
                if (bytesRead == -1) {
                    return;
                }

                int requestSize = bytesToInt(sizeBytes);

                byte[] requestBytes = new byte[requestSize];
                int totalRead = 0;
                while (totalRead < requestSize) {
                    int read = input.read(requestBytes, totalRead, requestSize - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }

                byte[] responseBytes = invoker.invoke(requestBytes);

                output.write(intToBytes(responseBytes.length));

                output.write(responseBytes);
                output.flush();

                System.out.println("[SRH] Resposta enviada para " + clientSocket.getInetAddress().getHostAddress());

            } catch (IOException e) {
                System.err.println("[SRH] Erro ao processar cliente: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("[SRH] Erro ao fechar socket: " + e.getMessage());
                }
            }
        }

        private int bytesToInt(byte[] bytes) {
            return ((bytes[0] & 0xFF) << 24) |
                   ((bytes[1] & 0xFF) << 16) |
                   ((bytes[2] & 0xFF) << 8) |
                   (bytes[3] & 0xFF);
        }

        private byte[] intToBytes(int value) {
            return new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
            };
        }
    }
}
