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
    private Broker broker;

    public GatewayService(RequestRouter requestRouter) {
        this.requestRouter  = requestRouter;
    }

    public void setBroker(Broker broker) {
        this.broker = broker;
    }

    @MethodMapping(method = MethodHTTP.GET, path = "aor")
    public String getAor(@Param(name = "object") String objectName) {
        if (broker == null) return "{\"error\": \"Broker não configurado\"}";
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