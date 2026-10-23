package io.metersphere.agent.service;

import io.metersphere.agent.service.gateway.MapGatewayClient;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.controller.handler.result.CommonResultCode;
import io.metersphere.system.security.LlmFeature;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class AgentModelFeatureBoundaryTests {
    @Test void disabledFeatureStopsRealPlanningGatewayAndWorkerEntryBeforeAnyDependencyCall() throws Exception {
        Object[] services = {new AgentRunnerService(), new AgentExecutionPlanningService(),
                new MapGatewayClient(RestClient.builder())};
        String[] methods = {"poll", "plan", "invokeStructured"};
        for (int i = 0; i < services.length; i++) {
            var factory = new AspectJProxyFactory(services[i]);
            factory.addAspect(new LlmFeature());
            Object proxy = factory.getProxy();
            for (var method : services[i].getClass().getMethods()) {
                if (!method.getName().equals(methods[i])) continue;
                var thrown = assertThrows(java.lang.reflect.InvocationTargetException.class,
                        () -> method.invoke(proxy, new Object[method.getParameterCount()]));
                assertInstanceOf(MSException.class, thrown.getCause());
                assertEquals(CommonResultCode.FEATURE_DISABLED, ((MSException) thrown.getCause()).getErrorCode());
            }
        }
    }
}
