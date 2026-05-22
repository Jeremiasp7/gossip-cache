package br.com.middleware.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.middleware.core.Marshaller;
import br.com.middleware.core.ServerRequestHandler;
import br.com.middleware.dto.InvocationRequest;

public class TcpPlugin implements ProtocolPlugin {
    
    private final ExecutorService executor = Executors.newFixedThreadPool(32);

    @Override
    public void start(int port, ServerRequestHandler serverRequestHandler, Marshaller marshaller) {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                System.out.println("[TcpPlugin] Ouvindo na porta " +port);
                while (true) {
                    Socket client = server.accept();
                    executor.submit(() -> handle(client, serverRequestHandler, marshaller));
                }
            } catch (IOException e) {
                System.out.println("[TcpPlugin] Erro fatal: " +e.getMessage());
            }
        }, "TcpPlugin-acceptor").start();
    }

    private void handle(Socket client, ServerRequestHandler serverRequestHandler, Marshaller marshaller) {
        try {
            client.setSoTimeout(5000);

            InputStream is = client.getInputStream();
            OutputStream os = client.getOutputStream();

            // read the request line
            String requestLine = readLine(is);
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            String httpMethod = parts[0];
            String fullUrl = parts[1];

            // separate path and query
            String path = fullUrl.contains("?") ? fullUrl.split("\\?")[0] : fullUrl;
            String query = fullUrl.contains("?") ? fullUrl.split("\\?")[1] : "";

            // /dictionary/get -> ["", "dictionary", "get"]
            String[] segments = path.split("/");
            String objectName = segments[1];
            String methodPath = segments[2];

            Map<String, String> params = parseQuery(query);

            // read headers
            int contentLength = 0;
            String line;
            while (!(line = readLine(is)).isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:"))
                    contentLength = Integer.parseInt(line.split(":", 2)[1].trim());
            }

            // the read of the body in post requisitions
            if (contentLength > 0) {
                byte[] bodyBytes = is.readNBytes(contentLength);
                params.putAll(parseQuery(new String(bodyBytes, StandardCharsets.UTF_8)));
            }

            // gather and invoke
            InvocationRequest request = marshaller.unmarshal(httpMethod, objectName, methodPath, params);
            String body = serverRequestHandler.handle(request);

            // httpResponse
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            String response  = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.write(bodyBytes);
            os.flush();
        } catch (SocketTimeoutException e) {
            System.err.println("[TcpPlugin] Timeout: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[TcpPlugin] Erro: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    // read a line of the input stream byte by byte, avoiding buffered reader
    private String readLine(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = is.read()) != -1) {
            if (b == '\r') {
                is.read(); // consome o \n
                break;
            }
            sb.append((char) b);
        }
        return sb.toString();
    }

    private Map<String, String> parseQuery(String query) {
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

    @Override
    public String getProtocolName() { return "TCP"; }
}
