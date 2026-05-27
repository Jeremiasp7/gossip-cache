package br.com.middleware.network;

import java.net.*;
import java.nio.charset.StandardCharsets;

import br.com.middleware.core.ServerRequestHandler;

public class UdpPlugin extends AbstractUdpServer implements ProtocolPlugin {

    private ServerRequestHandler srh;

    @Override
    public void init(ServerRequestHandler srh) {
        this.srh        = srh;
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

    // TCP não usado no UdpPlugin
    @Override
    public void handleHttpConnection(Socket connection) {}

    // Não usado — UdpStrategy é quem abre o DatagramSocket
    @Override
    protected void handlePacket(byte[] data, int offset, int length,
                                InetAddress addr, int port,
                                DatagramSocket socket) {}

    @Override
    public String getProtocolName() { return "UDP"; }

    @Override
    public ServerRequestHandler getServerRequestHandler() { return srh; }
}