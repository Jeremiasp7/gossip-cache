package br.com.middleware;

public class ObjectId {
    
    private final String name;     
    private final String fullPath; 

    public ObjectId(String name, String path) {
        this.name = name;
        this.fullPath = name + "/" + path;
    }

    public String getName() {
        return name;
    }

    public String getFullPath() {
        return fullPath;
    }

}
