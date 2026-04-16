package br.com.core.network;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.GossipMessage;
import br.com.core.model.Operation;

import java.nio.charset.StandardCharsets;

public class HttpParser {
    
    public AppRequest requestConvertor(String socketMessage) { // transform a socket message in a http request object

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

    public AppResponse responseConvertor(String socketMessage) { // transform a socket message in a http response object

        String rawResponse = socketMessage;
        String[] parts = rawResponse.split("\r\n\r\n");
        String headers = parts[0];
        String body = parts.length > 1 ? parts[1] : "";

        String[] headersLines = headers.split("\r\n");
        String[] requestLine = headersLines[0].split(" ");

        String status = requestLine[1];
        String message = requestLine.length > 2 ? requestLine[2] : "";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        AppResponse response = new AppResponse(status, bytes, message);

        return response;
    }

    public String responseGenerator(AppResponse response) { // transform a response in a string http response

        String value = response.getValue() != null ? new String(response.getValue(), StandardCharsets.UTF_8) : "";
        int contentLength = value.getBytes(StandardCharsets.UTF_8).length;

        String httpResponse = "HTTP/1.1 " + response.getStatus() + " " + response.getMessage() + "\r\n" +
                              "Connection: close\r\n" +
                              "Content-Length: " + contentLength + "\r\n" +
                              "\r\n" + 
                              value;

        return httpResponse;
    }

    public String requestGenerator(AppRequest request) { // transform a request in a string http response

        String value = request.getValue() != null ? new String(request.getValue(), StandardCharsets.UTF_8) : "";
        int contentLength = value.getBytes(StandardCharsets.UTF_8).length;

        String httpRequest = request.getOperation() + " /" + request.getKey() + " HTTP/1.1\r\n" +
                             "Host: internal-node\r\n" +
                             "Content-Length: " + contentLength + "\r\n" +
                             "\r\n" +
                             value;   

        return httpRequest;
    }
    
    public String gossipGenerator(GossipMessage message) {

        String value = message.getData().getValue() != null ? new String(message.getData().getValue(), 
                    StandardCharsets.UTF_8) : "";
        int contentLength = value.getBytes(StandardCharsets.UTF_8).length;

        String key = message.getData().getKey();

        String gossipMessage = "POST /gossip/" + key + " HTTP/1.1\r\n" +
                               "Host: internal-node\r\n" +
                               "X-Operation: " + message.getData().getOperation() + "\r\n" +
                               "Content-Length: " + contentLength + "\r\n" +
                               "\r\n" +
                               value;

        return gossipMessage;
    }
}
