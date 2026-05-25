package br.com.core.network;

import java.io.*;
import java.net.*;

// Wrapper que substitui o InputStream do Socket pelo PushbackInputStream
// que já tem o primeiro byte devolvido
public class SocketWithPushback extends Socket {

    private final Socket delegate;
    private final PushbackInputStream pbis;

    public SocketWithPushback(Socket delegate, PushbackInputStream pbis) {
        this.delegate = delegate;
        this.pbis     = pbis;
    }

    @Override
    public InputStream getInputStream() { return pbis; }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return delegate.getOutputStream();
    }

    @Override
    public void close() throws IOException { delegate.close(); }

    @Override
    public boolean isClosed() { return delegate.isClosed(); }

    @Override
    public void shutdownOutput() throws IOException { delegate.shutdownOutput(); }

    @Override
    public InetAddress getInetAddress() { return delegate.getInetAddress(); }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        delegate.setSoTimeout(timeout);
    }
}