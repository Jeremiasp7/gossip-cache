package network;

import model.AppRequest;
import model.AppResponse;
import model.Operation;

import java.nio.charset.StandardCharsets;

public class HttpParser {
    
    public AppRequest requestConvertor(String socketMessage) {

        String rawRequest = socketMessage;
        String[] parts = rawRequest.split("\r\n\r\n");
        String headers = parts[0];
        String body = parts.length > 1 ? parts[1] : "";

        String[] headerLines = headers.split("\r\n");
        String[] requestLine = headerLines[0].split(" ");
        String method = requestLine[0]; 

        String uri = requestLine[1];   
        String key = uri.substring(uri.lastIndexOf("/") + 1);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        Operation operationMethod = Operation.valueOf(method);

        AppRequest request = new AppRequest(operationMethod, key, bytes);

        return request;
    }

    public String responseGenerator(AppResponse response) {

        String value = response.getValue() != null ? new String(response.getValue(), StandardCharsets.UTF_8) : "";
        int contentLength = value.getBytes(StandardCharsets.UTF_8).length;

        String httpResponse = "HTTP/1.1 " + response.getStatus() + " " + response.getMessage() + "\r\n" +
                              "Content-Length: " + contentLength + "\r\n" +
                              "\r\n" + 
                              value;

        return httpResponse;
    }
    
}
