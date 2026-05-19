package br.com.middleware.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;

import br.com.middleware.dto.InvocationReply;
import br.com.middleware.dto.InvocationRequest;

public class RemoteObjectClient {

    private String host;
    private int port;

    public RemoteObjectClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Object invokeRemote(ObjectId objectId, String methodName, Object[] parameters) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(host, port)) {

            OutputStream output = socket.getOutputStream();
            InputStream input = socket.getInputStream();

            InvocationRequest request = new InvocationRequest(objectId, methodName, parameters);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(request);
                oos.flush();

                byte[] requestBytes = baos.toByteArray();

                output.write(intToBytes(requestBytes.length));

                output.write(requestBytes);
                output.flush();

                byte[] sizeBytes = new byte[4];
                int bytesRead = input.read(sizeBytes);
                if (bytesRead == -1) {
                    throw new IOException("Conexão fechada pelo servidor");
                }

                int responseSize = bytesToInt(sizeBytes);

                byte[] responseBytes = new byte[responseSize];
                int totalRead = 0;
                while (totalRead < responseSize) {
                    int read = input.read(responseBytes, totalRead, responseSize - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }

                try (ByteArrayInputStream bais = new ByteArrayInputStream(responseBytes);
                     ObjectInputStream ois = new ObjectInputStream(bais)) {

                    InvocationReply reply = (InvocationReply) ois.readObject();

                    if (reply.getErrorMessage() != null) {
                        throw new RuntimeException("Erro remoto: " + reply.getErrorMessage());
                    }

                    return reply.getResult();
                }
            }
        }
    }

    private int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8) |
               (bytes[3] & 0xFF);
    }

    private byte[] intToBytes(int value) {
        return new byte[]{
            (byte) ((value >> 24) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }
}
