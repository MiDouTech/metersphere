package io.metersphere.agent.service.gateway;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.Map;

@Component
public class MapGatewayRequestMapper {

    public Map<String, Object> toGatewayRequest(GatewayPlanningRequest request) {
        return JSON.parseObject(JSON.toJSONString(request), Map.class);
    }

    public GatewayPlanningResponse normalizeResponse(Map<?, ?> raw) {
        if (raw == null) {
            throw new MSException("MAP_GATEWAY_EMPTY_RESPONSE");
        }
        if (raw.get("error") != null) {
            throw new MSException("MAP_GATEWAY_REJECTED");
        }
        GatewayPlanningResponse response = JSON.parseObject(JSON.toJSONString(raw), GatewayPlanningResponse.class);
        if (StringUtils.isBlank(response.getGatewayRequestId())) {
            throw new MSException("MAP_GATEWAY_REQUEST_ID_MISSING");
        }
        if (response.getStructuredOutput() == null) {
            throw new MSException("MAP_GATEWAY_STRUCTURED_OUTPUT_MISSING");
        }
        return response;
    }

    public MSException mapError(Throwable error) {
        if (error instanceof MSException msException) {
            return msException;
        }
        if (error instanceof RestClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            String body = StringUtils.defaultString(responseException.getResponseBodyAsString()).toLowerCase(Locale.ROOT);
            if (status.value() == 429) return new MSException("MAP_GATEWAY_RATE_LIMITED");
            if (status.value() == 401 || status.value() == 403) return new MSException("MAP_GATEWAY_AUTH_FAILED");
            if (status.value() == 408 || status.value() == 504) return new MSException("MAP_GATEWAY_TIMEOUT");
            if (body.contains("balance") || body.contains("insufficient") || body.contains("余额")) {
                return new MSException("MAP_GATEWAY_BALANCE_INSUFFICIENT");
            }
            if (status.is4xxClientError()) return new MSException("MAP_GATEWAY_REQUEST_REJECTED");
            if (status.is5xxServerError()) return new MSException("MAP_GATEWAY_UPSTREAM_FAILURE");
        }
        if (error instanceof SocketTimeoutException || error.getCause() instanceof SocketTimeoutException) {
            return new MSException("MAP_GATEWAY_TIMEOUT");
        }
        if (error instanceof ConnectException || error.getCause() instanceof ConnectException) {
            return new MSException("MAP_GATEWAY_UNAVAILABLE");
        }
        String name = error.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return new MSException(name.contains("timeout") ? "MAP_GATEWAY_TIMEOUT" : "MAP_GATEWAY_UNAVAILABLE");
    }
}
