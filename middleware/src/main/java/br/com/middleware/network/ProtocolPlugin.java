package br.com.middleware.network;

import java.net.Socket;
import br.com.middleware.core.ServerRequestHandler;

public interface ProtocolPlugin {

    void init(ServerRequestHandler srh);

    void handleHttpConnection(Socket connection);

    void handleUdpPacket(byte[] data, int offset, int length,
                         java.net.InetAddress addr, int port,
                         java.net.DatagramSocket socket);

    String getProtocolName();

    ServerRequestHandler getServerRequestHandler();
}