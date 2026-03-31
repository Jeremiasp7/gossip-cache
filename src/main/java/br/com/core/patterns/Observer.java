package patterns;

import model.NodeInfo;

public interface Observer {

    public void update(NodeInfo node, EventType eventType); // notify the api gateway about events in the nodes

} 
