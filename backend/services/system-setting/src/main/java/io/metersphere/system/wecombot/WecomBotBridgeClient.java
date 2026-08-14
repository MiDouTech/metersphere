package io.metersphere.system.wecombot;

import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class WecomBotBridgeClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public Map<String, Object> status() {
        return request("GET", "/v1/status", null);
    }

    public Map<String, Object> configure(String botId, String secret, boolean enabled) {
        Map<String, Object> body = new HashMap<>();
        body.put("botId", botId);
        body.put("secret", secret);
        body.put("enabled", enabled);
        return request("POST", "/v1/configure", body);
    }

    public Map<String, Object> reconnect() {
        return request("POST", "/v1/reconnect", Map.of());
    }

    public WecomBotModels.BridgeResult send(WecomBotModels.BridgeSendRequest message) {
        try {
            Map<String, Object> body = Map.of(
                    "requestId", message.requestId(),
                    "outboxId", message.outboxId(),
                    "target", Map.of("type", "CHAT".equals(message.targetType()) ? "GROUP" : "USER", "id", message.targetId()),
                    "message", Map.of("type", "markdown", "content", message.payload().get("content")));
            Map<String, Object> response = request("POST", "/v1/messages/send", body);
            return new WecomBotModels.BridgeResult(Boolean.TRUE.equals(response.get("success")),
                    Boolean.TRUE.equals(response.get("retryable")), string(response.get("errorCode")),
                    string(response.get("errorMessage")));
        } catch (BridgeHttpException e) {
            return new WecomBotModels.BridgeResult(false, e.retryable, e.code, e.getMessage());
        } catch (Exception e) {
            return new WecomBotModels.BridgeResult(false, true, "BRIDGE_UNAVAILABLE", "Bridge unavailable");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(String method, String path, Object body) {
        String baseUrl = System.getenv().getOrDefault("MS_WECOM_BRIDGE_URL", "http://wecom-bot-bridge:8095");
        String token = WecomRuntimeSecrets.read("MS_WECOM_BRIDGE_TOKEN");
        if (StringUtils.isBlank(token)) {
            throw new MSException("MS_WECOM_BRIDGE_TOKEN is not configured");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json");
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body)));
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Map<String, Object> error = JSON.parseObject(response.body(), Map.class);
                throw new BridgeHttpException(Objects.toString(error.get("code"), "BRIDGE_HTTP_" + response.statusCode()),
                        Objects.toString(error.get("message"), "Bridge request failed"),
                        Boolean.TRUE.equals(error.get("retryable")));
            }
            return JSON.parseObject(response.body(), Map.class);
        } catch (BridgeHttpException | MSException e) {
            throw e;
        } catch (Exception e) {
            throw new MSException("WeCom Bot Bridge is unavailable");
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static final class BridgeHttpException extends RuntimeException {
        private final String code;
        private final boolean retryable;

        private BridgeHttpException(String code, String message, boolean retryable) {
            super(message);
            this.code = code;
            this.retryable = retryable;
        }
    }
}
