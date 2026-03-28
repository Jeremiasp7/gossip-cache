package model;

public class GossipMessage {
    
    private NodeInfo sourceNode; // the sender of the gossip
    private long sequenceNumber; // versioning variable
    private AppRequest data; // the content of operation
    private int hopCount; // a limit for the time of the message

    // class constructors
    public GossipMessage() {}

    public GossipMessage(NodeInfo sourceNode, long sequenceNumber, AppRequest data, int hopCount) {
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


    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
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
