package br.com.middleware.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.middleware.core.Marshaller;
import br.com.middleware.core.ServerRequestHandler;
import br.com.middleware.dto.InvocationRequest;

public class UdpPlugin implements ProtocolPlugin {
    
    private final ExecutorService executor = Executors.newFixedThreadPool(32);

    @Override
    public void start(int port, ServerRequestHandler serverRequestHandler, Marshaller marshaller) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(port)) {
                System.out.println("[UdpPlugin] Ouvindo na porta " +port);
                while (true) {
                    byte[] buf = new byte[8192];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    InetAddress address = packet.getAddress();
                    int senderPort = packet.getPort();
                    String raw = new String(
                        packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    executor.submit(() -> handle(
                        raw, address, senderPort, socket, serverRequestHandler, marshaller));
                }
            } catch (Exception e) {
                System.err.println("[UdpPlugin] Erro fatal: " + e.getMessage());
            }
        }, "UdpPlugin-receiver").start();
    }

    private void handle(String raw, InetAddress addr, int senderPort,
                        DatagramSocket socket, ServerRequestHandler srh,
                        Marshaller marshaller) {
        try {
            String httpMethod = extractJson(raw, "method");
            String objectName = extractJson(raw, "object");
            String methodPath = extractJson(raw, "path");
            String paramsJson = extractJsonObject(raw, "params");
            Map<String, String> params = parseJsonObject(paramsJson);

            InvocationRequest request = marshaller.unmarshal(
                httpMethod, objectName, methodPath, params);
            String body = srh.handle(request);

            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(response, response.length, addr, senderPort));

        } catch (Exception e) {
            System.err.println("[UdpPlugin] Erro ao processar: " + e.getMessage());
            try {
                String err = "{\"error\":\"" + e.getMessage() + "\"}";
                byte[] b   = err.getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(b, b.length, addr, senderPort));
            } catch (Exception ignored) {}
        }
    }

    // parsen json to avoid library dependation
    private String extractJson(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private String extractJsonObject(String json, String key) {
        String search = "\"" + key + "\":{";
        int start = json.indexOf(search);
        if (start == -1) return "{}";
        start += search.length() - 1;
        int depth = 0, end = start;
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '{') depth++;
            else if (json.charAt(i) == '}') { depth--; if (depth == 0) { end = i; break; } }
        }
        return json.substring(start, end + 1);
    }

    private Map<String, String> parseJsonObject(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        String inner = json.replaceAll("[{}]", "");
        for (String pair : inner.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String k = kv[0].trim().replace("\"", "");
                String v = kv[1].trim().replace("\"", "");
                map.put(k, v);
            }
        }
        return map;
    }

    @Override
    public String getProtocolName() { return "UDP"; }
}
