package br.com.middleware.network;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import br.com.middleware.core.ServerRequestHandler;

public class TcpPlugin implements ProtocolPlugin {

    private ServerRequestHandler srh;

    private static final int KEEPALIVE_TIMEOUT_SECONDS = 30;
    private static final int MAX_KEEPALIVE_REQUESTS = 1000;

    @Override
    public void init(ServerRequestHandler srh) {
        this.srh = srh;
        System.out.println("[TcpPlugin] Inicializado");
    }

    @Override
    public void handleHttpConnection(Socket connection) {
        try {
            connection.setSoTimeout((KEEPALIVE_TIMEOUT_SECONDS + 2) * 1000);

            InputStream  is = connection.getInputStream();
            OutputStream os = connection.getOutputStream();

            int requestCount = 0;
            boolean keepAlive = true;

            while (keepAlive && requestCount < MAX_KEEPALIVE_REQUESTS) {

                String requestLine = readLine(is);
                if (requestLine == null || requestLine.isEmpty()) break;

                HttpRequestParts parts = parseRequestLine(requestLine);

                boolean clientWantsClose = false;
                int contentLength = 0;
                String line;
                while (!(line = readLine(is)).isEmpty()) {
                    String lower = line.toLowerCase();
                    if (lower.startsWith("content-length:"))
                        contentLength = Integer.parseInt(line.split(":", 2)[1].trim());
                    if (lower.startsWith("connection:") && lower.contains("close"))
                        clientWantsClose = true;
                }

                keepAlive = !clientWantsClose;

                Map<String, String> params = parseQuery(parts.query);
                if (contentLength > 0) {
                    byte[] bodyBytes = is.readNBytes(contentLength);
                    params.putAll(parseQuery(
                        new String(bodyBytes, StandardCharsets.UTF_8)));
                }

                String body = srh.handle(
                    parts.httpMethod,
                    parts.objectName,
                    parts.methodPath,
                    params
                );

                boolean isError = body.contains("\"error\"");
                String httpStatus = isError ? "503 Service Unavailable" : "200 OK";
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

                requestCount++;

                boolean lastRequest = !keepAlive || requestCount >= MAX_KEEPALIVE_REQUESTS;

                String connectionHeader;
                String keepAliveHeader = "";

                if (lastRequest) {
                    connectionHeader = "Connection: close\r\n";
                } else {
                    connectionHeader = "Connection: keep-alive\r\n";
                    keepAliveHeader  = "Keep-Alive: timeout=" + KEEPALIVE_TIMEOUT_SECONDS
                                       + ", max=" + (MAX_KEEPALIVE_REQUESTS - requestCount)
                                       + "\r\n";
                }

                String response = "HTTP/1.1 " + httpStatus + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + bodyBytes.length + "\r\n"
                    + connectionHeader
                    + keepAliveHeader
                    + "\r\n";

                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.write(bodyBytes);
                os.flush();
            }

        } catch (SocketTimeoutException e) {
        } catch (EOFException | SocketException e) {
        } catch (Exception e) {
            System.err.println("[TcpPlugin] Erro: " + e.getMessage());
        } finally {
            try { connection.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public void handleUdpPacket(byte[] data, int offset, int length,
                                InetAddress addr, int port,
                                DatagramSocket socket) {}

    @Override
    public String getProtocolName() { return "TCP"; }

    private String readLine(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = is.read()) != -1) {
            if (b == '\r') { is.read(); break; }
            sb.append((char) b);
        }
        return sb.toString();
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
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

    private HttpRequestParts parseRequestLine(String requestLine) {
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

    private static class HttpRequestParts {
        final String httpMethod;
        final String objectName;
        final String methodPath;
        final String query;
        HttpRequestParts(String httpMethod, String objectName,
                        String methodPath, String query) {
            this.httpMethod = httpMethod;
            this.objectName = objectName;
            this.methodPath = methodPath;
            this.query      = query;
        }
    }
}
