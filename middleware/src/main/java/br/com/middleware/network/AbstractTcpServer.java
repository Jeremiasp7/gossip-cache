package br.com.middleware.network;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AbstractTcpServer {

    protected final ExecutorService executor = Executors.newFixedThreadPool(32);

    // Subclasses implementam o que fazer DEPOIS que a conexão foi aceita
    // Este método roda no thread do acceptor — deve ser rápido e não bloquear
    protected abstract void onAccepted(Socket connection);

    protected void listen(int port) {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port, 200)) { // ← backlog 200
                System.out.println("[" + getClass().getSimpleName()
                    + "] Escutando na porta " + port);
                while (true) {
                    Socket connection = server.accept();
                    onAccepted(connection);
                }
            } catch (IOException e) {
                System.err.println("[" + getClass().getSimpleName()
                    + "] Erro fatal: " + e.getMessage());
            }
        }, getClass().getSimpleName() + "-acceptor").start();
    }

    // Mantido para compatibilidade — TcpPlugin pode continuar usando se quiser
    protected void handleConnection(Socket connection) {}

    // demais métodos utilitários permanecem iguais
    protected String readLine(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = is.read()) != -1) {
            if (b == '\r') { is.read(); break; }
            sb.append((char) b);
        }
        return sb.toString();
    }

    protected Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2)
                map.put(
                    URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return map;
    }

    protected int readHeaders(InputStream is) throws IOException {
        int contentLength = 0;
        String line;
        while (!(line = readLine(is)).isEmpty()) {
            if (line.toLowerCase().startsWith("content-length:"))
                contentLength = Integer.parseInt(line.split(":", 2)[1].trim());
        }
        return contentLength;
    }

    protected HttpRequestParts parseRequestLine(String requestLine) {
        String[] parts    = requestLine.split(" ");
        String httpMethod = parts[0];
        String fullUrl    = parts[1];
        String path  = fullUrl.contains("?") ? fullUrl.split("\\?")[0] : fullUrl;
        String query = fullUrl.contains("?") ? fullUrl.split("\\?")[1] : "";
        String[] segments = path.split("/");
        String objectName = segments.length > 1 ? segments[1] : "";
        String methodPath = segments.length > 2 ? segments[2] : "";
        return new HttpRequestParts(httpMethod, objectName, methodPath, query);
    }

    protected static class HttpRequestParts {
        public final String httpMethod;
        public final String objectName;
        public final String methodPath;
        public final String query;
        public HttpRequestParts(String httpMethod, String objectName,
                                String methodPath, String query) {
            this.httpMethod = httpMethod;
            this.objectName = objectName;
            this.methodPath = methodPath;
            this.query      = query;
        }
    }
}