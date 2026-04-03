package br.com.core.dto;

public class CacheEntry {
    
    private long sequenceNumber;
    private byte[] value;

    public CacheEntry() {}

    public CacheEntry(long sequenceNumber, byte[] value) {
        this.sequenceNumber = sequenceNumber;
        this.value = value;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

}
