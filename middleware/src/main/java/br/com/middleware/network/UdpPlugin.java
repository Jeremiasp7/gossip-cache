package br.com.middleware.network;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import br.com.middleware.core.Marshaller;
import br.com.middleware.core.ServerRequestHandler;
import br.com.middleware.dto.InvocationRequest;

public class UdpPlugin extends AbstractUdpServer implements ProtocolPlugin {

    private ServerRequestHandler srh;
    private Marshaller marshaller;

    @Override
    public void start(int port, ServerRequestHandler srh, Marshaller marshaller) {
        this.srh        = srh;
        this.marshaller = marshaller;
        listen(port);
    }

    @Override
    protected void handlePacket(byte[] data, int offset, int length,
                                InetAddress addr, int port,
                                DatagramSocket socket) {
        try {
            String raw        = new String(data, offset, length, StandardCharsets.UTF_8);
            String httpMethod = extractJson(raw, "method");
            String objectName = extractJson(raw, "object");
            String methodPath = extractJson(raw, "path");
            String paramsJson = extractJsonObject(raw, "params");
            Map<String, String> params = parseJsonObject(paramsJson);

            InvocationRequest request = marshaller.unmarshal(
                httpMethod, objectName, methodPath, params);
            String body = srh.handle(request);

            sendResponse(socket, body.getBytes(StandardCharsets.UTF_8), addr, port);

        } catch (Exception e) {
            System.err.println("[UdpPlugin] Erro: " + e.getMessage());
            sendResponse(socket,
                ("{\"error\":\"" + e.getMessage() + "\"}")
                    .getBytes(StandardCharsets.UTF_8), addr, port);
        }
    }

    @Override
    public String getProtocolName() { return "UDP"; }
}