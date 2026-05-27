package br.com.middleware.network;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import br.com.middleware.core.Marshaller;
import br.com.middleware.core.ServerRequestHandler;
import br.com.middleware.dto.InvocationRequest;

public class TcpPlugin extends AbstractTcpServer implements ProtocolPlugin {

    private ServerRequestHandler srh;
    private Marshaller marshaller;

    @Override
    public void init(ServerRequestHandler srh, Marshaller marshaller) {
        this.srh        = srh;
        this.marshaller = marshaller;
        System.out.println("[TcpPlugin] Inicializado");
    }

    // Chamado pelo TcpStrategy quando detecta HTTP
    @Override
    public void handleHttpConnection(Socket connection) {
        try {
            connection.setSoTimeout(5000);
            InputStream is  = connection.getInputStream();
            OutputStream os = connection.getOutputStream();

            String requestLine = readLine(is);
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

            // Se o body contém erro, retorna 503 para o JMeter contabilizar
            boolean isError = body.contains("\"error\"");
            String httpStatus = isError ? "503 Service Unavailable" : "200 OK";

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            String response  = "HTTP/1.1 " + httpStatus + "\r\n"
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

    // TcpPlugin não abre porta — onAccepted nunca é chamado nele
    // Implementado apenas para satisfazer o contrato do AbstractTcpServer
    @Override
    protected void onAccepted(Socket connection) {}

    // UDP não usado no TcpPlugin
    @Override
    public void handleUdpPacket(byte[] data, int offset, int length,
                                InetAddress addr, int port,
                                DatagramSocket socket) {}

    @Override
    public String getProtocolName() { return "TCP"; }

    @Override
    public ServerRequestHandler getServerRequestHandler() { return srh; }

    @Override
    public Marshaller getMarshaller() { return marshaller; }
}