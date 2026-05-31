package br.com.middleware.network;

import java.net.*;
import java.nio.charset.StandardCharsets;

import br.com.middleware.core.ServerRequestHandler;

public class UdpPlugin implements ProtocolPlugin {

    private ServerRequestHandler srh;

    @Override
    public void init(ServerRequestHandler srh) {
        this.srh = srh;
        System.out.println("[UdpPlugin] Inicializado");
    }

    @Override
    public void handleUdpPacket(byte[] data, int offset, int length,
                                InetAddress addr, int port,
                                DatagramSocket socket) {
        try {
            //
        } catch (Exception e) {
            System.err.println("[UdpPlugin] Erro: " + e.getMessage());
            sendResponse(socket,
                ("{\"error\":\"" + e.getMessage() + "\"}")
                    .getBytes(StandardCharsets.UTF_8), addr, port);
        }
    }

    @Override
    public void handleHttpConnection(Socket connection) {}

    @Override
    public String getProtocolName() { return "UDP"; }

    protected void sendResponse(DatagramSocket socket, byte[] data,
                                InetAddress addr, int port) {
        try {
            socket.send(new DatagramPacket(data, data.length, addr, port));
        } catch (Exception e) {
            System.err.println("[UdpPlugin] Erro ao enviar resposta: " + e.getMessage());
        }
    }
}
