package br.com.middleware;

import java.util.UUID;

import br.com.middleware.core.Invoker;
import br.com.middleware.core.ObjectId;
import br.com.middleware.core.RemoteObjectClient;
import br.com.middleware.core.ServerRequestHandler;
import br.com.middleware.service.DictionaryService;

public class ExampleTest {

    public static void main(String[] args) throws Exception {
        Thread serverThread = new Thread(() -> {
            try {
                Invoker invoker = new Invoker();
                invoker.register(new DictionaryService());

                ServerRequestHandler srh = new ServerRequestHandler(8090, invoker);
                srh.start();

                Thread.currentThread().join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(1000);

        RemoteObjectClient client = new RemoteObjectClient("localhost", 8090);

        UUID uuid = UUID.randomUUID();
        ObjectId objectId = new ObjectId("DictionaryService", "/service", uuid);

        try {
            Object result = client.invokeRemote(objectId, "get", new Object[]{"user123"});
            System.out.println("[Client] Resultado da invocação: " + result);

        } catch (Exception e) {
            System.err.println("[Client] Erro na invocação: " + e.getMessage());
            e.printStackTrace();
        }

        System.exit(0);
    }
}
