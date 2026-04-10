package br.com.gateway;

import br.com.core.model.NodeInfo;

public class ServiceRegistry {
    
    private NodeInfo nodeWriter;
    private NodeInfo nodeReader;

    public void registerReader(NodeInfo node) {
        this.nodeReader = node;
    }

    public void registerWriter(NodeInfo node) {
        this.nodeWriter = node;
    }

    public NodeInfo getReader() {
        return this.nodeReader;
    }

    public NodeInfo getWriter() {
        return this.nodeWriter;
    }
    
}
