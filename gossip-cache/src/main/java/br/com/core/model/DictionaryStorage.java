package br.com.core.model;

import java.util.concurrent.ConcurrentHashMap;

import br.com.core.dto.CacheEntry;

public class DictionaryStorage {
    
    private ConcurrentHashMap<String, CacheEntry> mapInMemory = new ConcurrentHashMap<>(); // all the methods will read and write in this object

    public void saveLocalData(String key, byte[] value) { // packages and writes the data
        long actualTimestamp = System.currentTimeMillis();
        CacheEntry packet = new CacheEntry(actualTimestamp, value);
        this.mapInMemory.put(key, packet);
    }

    public byte[] searchData(String key) { // search a data in the memory
        CacheEntry cache = mapInMemory.get(key);
        if (cache == null || cache.getValue() == null) {
            return null; 
        }

        return cache.getValue(); 
    }

    public void applyGossip(String key, byte[] comingValue, long comingTimestamp) { // make the gossip work when a node actualize
        CacheEntry cache = mapInMemory.get(key);
        if (cache != null) {
            if (comingTimestamp > cache.getSequenceNumber()) {
                cache.setValue(comingValue);
                cache.setSequenceNumber(comingTimestamp);
            }
        } else {
            CacheEntry packet = new CacheEntry(comingTimestamp, comingValue);
            this.mapInMemory.put(key, packet);
        }
    }

    public void deleteLocalData(String key) { // delete the data and make a tombstone
        long actualTimestamp = System.currentTimeMillis();
        CacheEntry packet = new CacheEntry(actualTimestamp, null);
        this.mapInMemory.put(key, packet);
    }

}
