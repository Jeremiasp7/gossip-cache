package br.com.gateway;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.Operation;
import br.com.middleware.lifecycle.Lifecycle;
import br.com.middleware.lifecycle.LifecycleMode;
import br.com.middleware.annotations.MethodHTTP;
import br.com.middleware.annotations.MethodMapping;
import br.com.middleware.annotations.Param;
import br.com.middleware.annotations.RemoteObject;
import br.com.middleware.core.AbsoluteObjectReference;
import br.com.middleware.core.Broker;

@RemoteObject(name = "gateway")
@Lifecycle(value = LifecycleMode.STATIC)
public class GatewayService {

    private final RequestRouter requestRouter;
    private final Broker broker;
    private static long instanceCount = 0;
    private final long instanceId;

    public GatewayService(RequestRouter requestRouter, Broker broker) {
        this.requestRouter  = requestRouter;
        this.broker = broker;
        this.instanceId = ++instanceCount;
        System.out.println("[GatewayService] ✓ Instância #" + instanceId
            + " criada (Lifecycle=" + GatewayService.class.getAnnotation(Lifecycle.class).value() + ")");
    }

    @MethodMapping(method = MethodHTTP.GET, path = "aor")
    public String getAor(@Param(name = "object") String objectName) {
        try {
            AbsoluteObjectReference aor = broker.getAor(objectName);
            return aor.toBaseUrl();
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @MethodMapping(method = MethodHTTP.POST, path = "post")
    public String post(@Param(name = "key") String key,
                    @Param(name = "value") String value) {
        AppRequest request = new AppRequest(Operation.POST, key, value.getBytes());
        AppResponse response = requestRouter.routeRequest(request);

        if (!"200".equals(response.getStatus())) {
            throw new RuntimeException(response.getStatus()
                + " - " + response.getMessage());
        }
        return response.getStatus() + " - " + response.getMessage();
    }

    @MethodMapping(method = MethodHTTP.GET, path = "get")
    public String get(@Param(name = "key") String key) {
        AppRequest request = new AppRequest(Operation.GET, key, null);
        AppResponse response = requestRouter.routeRequest(request);

        if (!"200".equals(response.getStatus())) {
            throw new RuntimeException(response.getStatus()
                + " - " + response.getMessage());
        }
        if (response.getValue() == null) return "null";
        return new String(response.getValue());
    }
}