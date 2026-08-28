package io.metersphere.agent.service;

import io.metersphere.agent.secret.AgentSecretProviderRegistry;
import io.metersphere.agent.service.gateway.GatewayPlanningRequest;
import io.metersphere.agent.service.gateway.MapGatewayClient;
import io.metersphere.agent.service.gateway.MapGatewayRequestMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

class MapGatewayClientContractTests {
    @Test void rejectsNonTlsGatewayAndNonOpaqueServiceKeyBeforeNetworkCall(){
        MapGatewayClient client=new MapGatewayClient(RestClient.builder());ReflectionTestUtils.setField(client,"providers",Mockito.mock(AgentSecretProviderRegistry.class));ReflectionTestUtils.setField(client,"requestMapper",Mockito.mock(MapGatewayRequestMapper.class));
        GatewayPlanningRequest request=new GatewayPlanningRequest();request.setMetadata(Map.of("taskId","t1","projectId","p1"));request.setTraceId("trace");
        ReflectionTestUtils.setField(client,"baseUrl","http://gateway.local");
        Assertions.assertEquals("MAP_GATEWAY_URL_INVALID",Assertions.assertThrows(MSException.class,()->client.invokeStructured(request,"vault://key")).getMessage());
        ReflectionTestUtils.setField(client,"baseUrl","https://gateway.local");
        Assertions.assertEquals("MAP_GATEWAY_SERVICE_KEY_REF_INVALID",Assertions.assertThrows(MSException.class,()->client.invokeStructured(request,"plain-key")).getMessage());
    }
}
