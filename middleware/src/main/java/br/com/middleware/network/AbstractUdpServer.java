package br.com.middleware.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AbstractUdpServer {

    protected final ExecutorService executor = Executors.newFixedThreadPool(32);

    protected abstract void handlePacket(byte[] data, int offset, int length,
                                         InetAddress addr, int port,
                                         DatagramSocket socket);

    protected void listen(int port) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(port)) {
                System.out.println("[" + getClass().getSimpleName()
                    + "] Escutando na porta " + port);
                while (true) {
                    byte[] buf    = new byte[8192];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    byte[] data   = packet.getData();
                    int offset    = packet.getOffset();
                    int length    = packet.getLength();
                    InetAddress addr = packet.getAddress();
                    int senderPort   = packet.getPort();

                    executor.submit(() -> handlePacket(
                        data, offset, length, addr, senderPort, socket));
                }
            } catch (Exception e) {
                System.err.println("[" + getClass().getSimpleName()
                    + "] Erro fatal: " + e.getMessage());
            }
        }, getClass().getSimpleName() + "-receiver").start();
    }

    protected String extractJson(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    protected String extractJsonObject(String json, String key) {
        String search = "\"" + key + "\":{";
        int start = json.indexOf(search);
        if (start == -1) return "{}";
        start += search.length() - 1;
        int depth = 0, end = start;
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '{') depth++;
            else if (json.charAt(i) == '}') {
                depth--;
                if (depth == 0) { end = i; break; }
            }
        }
        return json.substring(start, end + 1);
    }

    protected Map<String, String> parseJsonObject(String json) {
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

    protected void sendResponse(DatagramSocket socket, byte[] data,
                                InetAddress addr, int port) {
        try {
            socket.send(new DatagramPacket(data, data.length, addr, port));
        } catch (Exception e) {
            System.err.println("[" + getClass().getSimpleName()
                + "] Erro ao enviar resposta: " + e.getMessage());
        }
    }
}