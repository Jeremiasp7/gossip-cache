package br.com.gateway;

import java.util.List;
import java.util.Random;

import br.com.core.gossip.MembershipList;
import br.com.core.model.NodeInfo;

public class ServiceRegistry {
    
    private MembershipList membershipList;
    private Random random;

    public ServiceRegistry(MembershipList membershipList) {
        this.membershipList = membershipList;
        this.random = new Random();
    }

    public NodeInfo getReaders() {
        List<NodeInfo> readers = membershipList.getReaderNodes();
        
        if (readers.isEmpty()) {
            return null;
        }

        return readers.get(random.nextInt(readers.size()));
    }

    public NodeInfo getWriters() {
        List<NodeInfo> writers = membershipList.getWriterNodes();

        if (writers.isEmpty()) {
            return null;
        }

        return writers.get(random.nextInt(writers.size()));
    }
    
}
