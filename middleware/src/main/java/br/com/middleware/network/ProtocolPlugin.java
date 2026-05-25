package br.com.middleware.network;

import java.net.Socket;
import br.com.middleware.core.Marshaller;
import br.com.middleware.core.ServerRequestHandler;

public interface ProtocolPlugin {

    // Inicializa o plugin com as dependências do middleware
    // Chamado pelos servidores (WriterServer, ReaderServer, ApiGatewayServer)
    void init(ServerRequestHandler srh, Marshaller marshaller);

    // Processa uma conexão HTTP já aceita — chamado pelo TcpStrategy
    void handleHttpConnection(Socket connection);

    // Processa um pacote UDP já recebido — chamado pelo UdpStrategy
    void handleUdpPacket(byte[] data, int offset, int length,
                         java.net.InetAddress addr, int port,
                         java.net.DatagramSocket socket);

    String getProtocolName();
}