package io.metersphere.agent.service.gateway;

import io.metersphere.agent.secret.AgentSecretProviderRegistry;
import io.metersphere.agent.secret.ResolvedSecret;
import io.metersphere.agent.secret.SecretResolveContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

@Service
public class MapGatewayClient {
    @Value("${metersphere.ai.map-gateway.base-url:}") private String baseUrl;
    @Value("${metersphere.ai.map-gateway.structured-path:/v1/model-gateway/structured-responses}") private String structuredPath;
    @Value("${metersphere.ai.map-gateway.health-path:/v1/model-gateway/health}") private String healthPath;
    @Value("${metersphere.ai.map-gateway.capabilities-path:/v1/model-gateway/capabilities}") private String capabilitiesPath;
    @Value("${metersphere.ai.map-gateway.connect-timeout-ms:3000}") private int connectTimeoutMs;
    @Value("${metersphere.ai.map-gateway.request-timeout-ms:60000}") private int requestTimeoutMs;
    @Resource private AgentSecretProviderRegistry providers;
    @Resource private MapGatewayRequestMapper requestMapper;
    private final RestClient.Builder restClientBuilder;

    public MapGatewayClient(RestClient.Builder builder) {
        this.restClientBuilder = builder;
    }

    public GatewayPlanningResponse invokeStructured(GatewayPlanningRequest request, String serviceKeyRef) {
        validateConfiguration();
        SecretResolveContext context = new SecretResolveContext(
                String.valueOf(request.getMetadata().get("taskId")), null,
                String.valueOf(request.getMetadata().get("projectId")), null, null,
                "MAP_GATEWAY_INVOKE", request.getTraceId());
        String providerType = providerType(serviceKeyRef);
        try (ResolvedSecret secret = providers.require(providerType).resolve(serviceKeyRef, null, context)) {
            char[] value = secret.valueCopy();
            try {
                String key = new String(value);
                Map<?, ?> raw = client(request.getTimeoutMs()).post().uri(resolve(structuredPath)).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .header("X-App-Caller", request.getAppCaller())
                        .header("X-Idempotency-Key", request.getIdempotencyKey())
                        .header("X-Trace-Id", request.getTraceId())
                        .body(requestMapper.toGatewayRequest(request)).retrieve().body(Map.class);
                return requestMapper.normalizeResponse(raw);
            } finally {
                java.util.Arrays.fill(value, '\0');
            }
        } catch (MSException ex) {
            throw ex;
        } catch (Exception ex) {
            throw requestMapper.mapError(ex);
        }
    }

    public Map<String, Object> health(String appCaller, String serviceKeyRef, String traceId) {
        return getProtected(healthPath, appCaller, serviceKeyRef, traceId);
    }

    public Map<String, Object> capabilities(String appCaller, String serviceKeyRef, String logicalModelId, String traceId) {
        return getProtected(capabilitiesPath + "?logicalModelPublicId=" + logicalModelId, appCaller, serviceKeyRef, traceId);
    }

    private Map<String, Object> getProtected(String path, String appCaller, String ref, String traceId) {
        validateConfiguration();
        SecretResolveContext context = new SecretResolveContext(null, null, null, null, null, "MAP_GATEWAY_VERIFY", traceId);
        char[] value = null;
        try (ResolvedSecret secret = providers.require(providerType(ref)).resolve(ref, null, context)) {
            value = secret.valueCopy();
            String key = new String(value);
            Map<?, ?> result = client(requestTimeoutMs).get().uri(resolve(path)).accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .header("X-App-Caller", appCaller).header("X-Trace-Id", traceId)
                    .retrieve().body(Map.class);
            return result == null ? Map.of() : JSON.parseObject(JSON.toJSONString(result), Map.class);
        } catch (Exception ex) {
            throw requestMapper.mapError(ex);
        } finally {
            if (value != null) java.util.Arrays.fill(value, '\0');
        }
    }

    private void validateConfiguration() {
        if (StringUtils.isBlank(baseUrl)) throw new MSException("MAP_GATEWAY_NOT_CONFIGURED");
        URI uri = URI.create(baseUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new MSException("MAP_GATEWAY_URL_INVALID");
        }
    }

    private URI resolve(String path) {
        return URI.create(StringUtils.removeEnd(baseUrl, "/") + "/" + StringUtils.removeStart(path, "/"));
    }

    private String providerType(String ref) {
        String scheme = URI.create(ref).getScheme();
        if (scheme == null) throw new MSException("MAP_GATEWAY_SERVICE_KEY_REF_INVALID");
        return scheme.toUpperCase(Locale.ROOT);
    }

    private RestClient client(Integer timeoutOverrideMs) {
        int timeout = timeoutOverrideMs == null ? requestTimeoutMs : Math.min(Math.max(timeoutOverrideMs, 1000), requestTimeoutMs);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(connectTimeoutMs, 100));
        factory.setReadTimeout(timeout);
        return restClientBuilder.clone().requestFactory(factory).build();
    }
}
