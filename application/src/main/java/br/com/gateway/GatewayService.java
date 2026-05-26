package br.com.gateway;

import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.Operation;
import br.com.core.gossip.MembershipList;
import br.com.middleware.lifecycle.Lifecycle;
import br.com.middleware.lifecycle.LifecycleMode;
import br.com.middleware.annotations.MethodHTTP;
import br.com.middleware.annotations.MethodMapping;
import br.com.middleware.annotations.Param;
import br.com.middleware.annotations.RemoteObject;

@RemoteObject(name = "gateway")
@Lifecycle(value = LifecycleMode.STATIC)
public class GatewayService {

    private final RequestRouter requestRouter;
    private final MembershipList membershipList;

    public GatewayService(RequestRouter requestRouter,
                          MembershipList membershipList) {
        this.requestRouter  = requestRouter;
        this.membershipList = membershipList;
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