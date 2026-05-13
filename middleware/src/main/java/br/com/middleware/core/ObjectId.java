package br.com.middleware.core;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class ObjectId implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String resourceName;
    private final String resourcePath;
    private final UUID uniqueId;

    public ObjectId(String resourceName, String resourcePath) {
        this.resourceName = Objects.requireNonNull(resourceName, "Resource name cannot be null");
        this.resourcePath = Objects.requireNonNull(resourcePath, "Resource path cannot be null");
        this.uniqueId = UUID.randomUUID();
    }

    public ObjectId(String resourceName, String resourcePath, UUID uniqueId) {
        this.resourceName = Objects.requireNonNull(resourceName, "Resource name cannot be null");
        this.resourcePath = Objects.requireNonNull(resourcePath, "Resource path cannot be null");
        this.uniqueId = Objects.requireNonNull(uniqueId, "Unique ID cannot be null");
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    // a method for get a complete and unique representation of the id
    public String getFullIdentifier() {
        return String.format("%s/%s/%s", resourceName, resourcePath, uniqueId.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectId objectId = (ObjectId) o;
        return resourceName.equals(objectId.resourceName) &&
               resourcePath.equals(objectId.resourcePath) &&
               uniqueId.equals(objectId.uniqueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceName, resourcePath, uniqueId);
    }

    @Override
    public String toString() {
        return getFullIdentifier();
    }
}