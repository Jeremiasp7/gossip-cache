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

    // Tempo máximo de inatividade antes de fechar a conexão keep-alive (segundos).
    // Anunciado ao cliente via header "Keep-Alive: timeout=N" para que ele feche
    // antes do servidor — elimina a race condition de socket morto.
    private static final int KEEPALIVE_TIMEOUT_SECONDS = 30;

    // Número máximo de requests por conexão keep-alive.
    private static final int MAX_KEEPALIVE_REQUESTS = 1000;

    @Override
    public void init(ServerRequestHandler srh, Marshaller marshaller) {
        this.srh        = srh;
        this.marshaller = marshaller;
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

                InvocationRequest request = marshaller.unmarshal(
                    parts.httpMethod, parts.objectName, parts.methodPath, params);
                String body = srh.handle(request);

                boolean isError = body.contains("\"error\"");
                String httpStatus = isError ? "503 Service Unavailable" : "200 OK";
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

                requestCount++;

                // Fecha na última iteração permitida para dar ao cliente
                // a chance de ver o "Connection: close" antes de reenviar.
                boolean lastRequest = !keepAlive
                        || requestCount >= MAX_KEEPALIVE_REQUESTS;

                String connectionHeader;
                String keepAliveHeader = "";

                if (lastRequest) {
                    connectionHeader = "Connection: close\r\n";
                } else {
                    // Anuncia o timeout exato para o cliente fechar antes do servidor.
                    // O JMeter respeita esse header e expira a conexão do seu lado
                    // KEEPALIVE_TIMEOUT_SECONDS antes do servidor fechar —
                    // eliminando a race condition que causava o SocketException de 1%.
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
            // Inatividade normal — cliente já fechou ou ficou silencioso
        } catch (EOFException | SocketException e) {
            // Cliente fechou a conexão — esperado em keep-alive
        } catch (Exception e) {
            System.err.println("[TcpPlugin] Erro: " + e.getMessage());
        } finally {
            try { connection.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    protected void onAccepted(Socket connection) {}

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