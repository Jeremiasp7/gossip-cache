package br.com.middleware.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import br.com.middleware.core.Marshaller;
import br.com.middleware.core.ServerRequestHandler;
import br.com.middleware.dto.InvocationRequest;

public class TcpPlugin extends AbstractTcpServer implements ProtocolPlugin {

    private ServerRequestHandler srh;
    private Marshaller marshaller;

    @Override
    public void start(int port, ServerRequestHandler srh, Marshaller marshaller) {
        this.srh        = srh;
        this.marshaller = marshaller;
        listen(port); // delegado para AbstractTcpServer
    }

    @Override
    protected void handleConnection(Socket connection) {
        try {
            connection.setSoTimeout(5000);
            InputStream is  = connection.getInputStream();
            OutputStream os = connection.getOutputStream();

            String requestLine = readLine(is); // reutiliza método da classe base
            if (requestLine == null || requestLine.isEmpty()) return;

            HttpRequestParts parts     = parseRequestLine(requestLine);
            int contentLength          = readHeaders(is);
            Map<String, String> params = parseQuery(parts.query);

            if (contentLength > 0) {
                byte[] bodyBytes = is.readNBytes(contentLength);
                params.putAll(parseQuery(
                    new String(bodyBytes, StandardCharsets.UTF_8)));
            }

            InvocationRequest request = marshaller.unmarshal(
                parts.httpMethod, parts.objectName, parts.methodPath, params);
            String body = srh.handle(request);

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
            try { connection.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public String getProtocolName() { return "TCP"; }
}