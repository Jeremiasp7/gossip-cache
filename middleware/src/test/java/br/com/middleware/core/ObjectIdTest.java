package br.com.middleware.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

public class ObjectIdTest {
    
    @Test
    public void testObjectIdCreation() {
        ObjectId id = new ObjectId("dictionary", "key1");
        
        assertEquals("dictionary", id.getResourceName());
        assertEquals("key1", id.getResourcePath());
        assertNotNull(id.getUniqueId());
    }

    @Test
    public void testObjectIdEquality() {
        UUID sharedUuid = UUID.randomUUID();
        ObjectId id1 = new ObjectId("cache", "item", sharedUuid);
        ObjectId id2 = new ObjectId("cache", "item", sharedUuid);
        
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    public void testObjectIdInequality() {
        ObjectId id1 = new ObjectId("cache", "item");
        ObjectId id2 = new ObjectId("cache", "item"); // Terá um UUID diferente
        
        assertNotEquals(id1, id2);
    }
}
