package br.com.core.model;

import java.io.Serializable;
import java.util.UUID;

public class GossipMessage implements Serializable {
    
    private NodeInfo sourceNode; // the sender of the gossip
    private UUID sequenceNumber; // versioning variable
    private AppRequest data; // the content of operation
    private int hopCount; // a limit for the time of the message

    // class constructors
    public GossipMessage() {}

    public GossipMessage(NodeInfo sourceNode, UUID sequenceNumber, AppRequest data, int hopCount) {
        this.sourceNode = sourceNode;
        this.sequenceNumber = sequenceNumber;
        this.data = data;
        this.hopCount = hopCount;
    }

    // getters and setters
    public NodeInfo getSourceNode() {
        return sourceNode;
    }

    public void setSourceNode(NodeInfo sourceNode) {
        this.sourceNode = sourceNode;
    }


    public UUID getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(UUID sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public AppRequest getData() {
        return data;
    }

    public void setData(AppRequest data) {
        this.data = data;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }

}
