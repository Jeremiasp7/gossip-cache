package br.com.middleware.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import br.com.middleware.dto.InvocationReply;
import br.com.middleware.dto.InvocationRequest;

public class Marshaller {
    
    public InvocationRequest unmarshal(byte[] bytes) throws IOException, ClassNotFoundException {

        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
            ObjectInputStream objectStream = new ObjectInputStream(byteStream)) {
            
            Object desserializedObject = objectStream.readObject();
            
            return (InvocationRequest) desserializedObject;
        }
    }

    public byte[] marshal(InvocationReply reply) throws IOException {

        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            
            objectStream.writeObject(reply);
            objectStream.flush();
            
            return byteStream.toByteArray();
        }
    }


}
