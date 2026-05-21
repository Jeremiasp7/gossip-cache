package br.com.middleware.network;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import br.com.middleware.core.*;
import br.com.middleware.dto.InvocationRequest;

public class HttpPlugin {

    private final ServerRequestHandler srh;
    private final Marshaller marshaller;
    private final Lookup lookup;
    private final int port;
    private final ExecutorService executor = Executors.newFixedThreadPool(16);

    public HttpPlugin(int port, Lookup lookup,
                      Marshaller marshaller, ServerRequestHandler srh) {
        this.port       = port;
        this.lookup     = lookup;
        this.marshaller = marshaller;
        this.srh        = srh;
    }

    public void start() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                System.out.println("[HttpPlugin] Ouvindo na porta " + port);
                while (true) {
                    Socket client = server.accept();
                    executor.submit(() -> handle(client));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handle(Socket client) {
        try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream()));
            
            PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {
            client.setSoTimeout(5000);
            // Linha de requisição: "GET /dictionary/get?key=foo HTTP/1.1"
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts     = requestLine.split(" ");
            String httpMethod  = parts[0];             // GET
            String fullUrl     = parts[1];             // /dictionary/get?key=foo

            // Separar path e query string
            String path  = fullUrl.contains("?") ? fullUrl.split("\\?")[0] : fullUrl;
            String query = fullUrl.contains("?") ? fullUrl.split("\\?")[1] : "";

            // /dictionary/get → ["", "dictionary", "get"]
            String[] segments  = path.split("/");
            String objectName  = segments[1];          // "dictionary"
            String methodPath  = segments[2];          // "get"

            // Parsear query string
            Map<String, String> params = parseQuery(query);

            // Ler headers até linha vazia, depois body (para POST)
            String line;
            int contentLength = 0;
            while (!(line = in.readLine()).isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:"))
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
            }
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                in.read(bodyChars, 0, contentLength);
                params.putAll(parseQuery(new String(bodyChars)));
            }

            // Montar e executar
            InvocationRequest request = marshaller.unmarshal(
                httpMethod, objectName, methodPath, params);
            String body = srh.handle(request);

            // Resposta HTTP
            String response = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.getBytes().length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + body;
            out.print(response);
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2)
                map.put(
                    URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                );
        }
        return map;
    }
}