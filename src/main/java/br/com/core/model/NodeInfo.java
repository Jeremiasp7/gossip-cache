package br.com.core.model;

import java.io.Serializable;
import java.util.UUID;

public class NodeInfo implements Serializable {
    
    private UUID id; // the node identifier
    private String address; // hostname of the node
    private int port; // the port where the node is
    private long lastHeartbeat; // the last heartbeat of the node
    private NodeType type;

    // class constructors
    public NodeInfo() {}

    public NodeInfo(UUID id, String address, int port, long lastHeartbeat, NodeType type) {
        this.id = id;
        this.address = address;
        this.port = port;
        this.lastHeartbeat = lastHeartbeat;
        this.type = type;
    }

    //getters and setters
    public UUID getSequenceNumber() {
        return id;
    }

    public void setSequenceNumber(UUID id) {
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }
    
    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }
}
