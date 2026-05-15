package br.com.middleware.dto;

import java.io.Serializable;

public class InvocationReply implements Serializable {
    private static final long serialVersionUID = 1L;

    private Object result;
    private String errorMessage; 

    public InvocationReply(Object result, String errorMessage) {
        this.result = result;
        this.errorMessage = errorMessage;
    }

    public Object getResult() { 
        return result; 
    }

    public String getErrorMessage() { 
        return errorMessage; 
    }
}