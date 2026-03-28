package model;

import java.util.UUID;

public class NodeInfo {
    
    private UUID id; // the node identifier
    private String address; // hostname of the node
    private int port; // the port where the node is

    // class constructors
    public NodeInfo() {}

    public NodeInfo(UUID id, String address, int port) {
        this.id = id;
        this.address = address;
        this.port = port;
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
    
}
