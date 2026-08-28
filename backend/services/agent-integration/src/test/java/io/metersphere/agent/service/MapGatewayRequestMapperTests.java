package io.metersphere.agent.service;

import io.metersphere.agent.service.gateway.MapGatewayRequestMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MapGatewayRequestMapperTests {
    private final MapGatewayRequestMapper mapper=new MapGatewayRequestMapper();
    @Test void normalizesOnlyStructuredResponsesWithRequestId(){
        assertThrows(MSException.class,()->mapper.normalizeResponse(Map.of("structuredOutput",Map.of())));
        assertDoesNotThrow(()->mapper.normalizeResponse(Map.of("gatewayRequestId","r1","structuredOutput",Map.of("ok",true))));
    }
    @Test void mapsRateLimitAndBalanceWithoutLeakingProviderBody(){
        MSException rate=mapper.mapError(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,"limited",null,new byte[0], StandardCharsets.UTF_8));
        assertEquals("MAP_GATEWAY_RATE_LIMITED",rate.getMessage());
        MSException balance=mapper.mapError(HttpClientErrorException.create(HttpStatus.BAD_REQUEST,"bad",null,"insufficient balance".getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8));
        assertEquals("MAP_GATEWAY_BALANCE_INSUFFICIENT",balance.getMessage());
    }
}
