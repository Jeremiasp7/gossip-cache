package br.com.core.model;

import java.io.Serializable;

public class AppResponse implements Serializable {
    
    private String status; // the response status of request
    private byte[] value; // the value message of the node
    private String message; // the message of response
    
    // class constructos
    public AppResponse() {}

    public AppResponse(String status, byte[] value, String message) {
        this.status = status;
        this.value = value;
        this.message = message;
    }
    
    // getters and setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }


}
