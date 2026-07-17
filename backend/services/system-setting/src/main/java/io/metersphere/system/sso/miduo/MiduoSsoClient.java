package io.metersphere.system.sso.miduo;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.config.MiduoSsoProperties;
import io.metersphere.system.dto.sso.miduo.MiduoRefreshResult;
import io.metersphere.system.dto.sso.miduo.MiduoValidateResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 米多星球开放 API 客户端（第三方后端专用）。
 * <p>
 * 对齐约定：
 * <ul>
 *   <li>HMAC-SHA256 签名，输出 Base64</li>
 *   <li>X-App-Timestamp 为 Unix <b>秒</b></li>
 *   <li>包装字段 return_code / return_msg / return_data</li>
 *   <li>业务成功：validate 看 valid==true；refresh/revoke 看 success==true</li>
 * </ul>
 */
@Service
public class MiduoSsoClient {

    private static final Logger log = LoggerFactory.getLogger(MiduoSsoClient.class);
    private static final String HMAC_ALG = "HmacSHA256";
    private static final String NONCE_KEY_PREFIX = "miduo:sso:nonce:";

    private static final String PATH_VALIDATE = "/api/open/sso/validate-login-token";
    private static final String PATH_REFRESH = "/api/open/sso/refresh-session-token";
    private static final String PATH_REVOKE = "/api/open/sso/revoke-session-token";
    private static final String PATH_BRIDGE = "/api/sso/bridge/redirect-url";

    @Resource
    private MiduoSsoProperties properties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setConnectionRequestTimeout(5000);
        factory.setReadTimeout(15000);
        restTemplate = new RestTemplate(factory);
    }

    public MiduoValidateResult validateLoginToken(String exchangeToken) {
        ensureEnabled();
        if (StringUtils.isBlank(exchangeToken)) {
            throw new MiduoSsoException("exchange token 不能为空");
        }
        Map<String, Object> data = postSigned(PATH_VALIDATE, exchangeToken, Map.of("token", exchangeToken));
        MiduoValidateResult result = new MiduoValidateResult();
        result.setValid(Boolean.TRUE.equals(asBoolean(data.get("valid"))));
        if (!result.isValid()) {
            String msg = asString(data.get("message"));
            log.warn("miduo validate failed, token={} msg={}", MiduoSsoLogUtils.maskToken(exchangeToken), msg);
            throw new MiduoSsoException(StringUtils.defaultIfBlank(msg, "米多登录校验失败"));
        }
        result.setSessionToken(asString(firstNonBlank(data, "sessionToken", "session_token")));
        // 身份权威字段：wework_userid（禁止用 URL 上的 mobile/name）
        result.setWeworkUserid(asString(firstNonBlank(data,
                "wework_userid", "weworkUserid", "employeeNo")));
        result.setExpiresAt(asEpochMillis(firstNonBlank(data,
                "sessionExpiresAt", "session_expires_at", "expiresAt", "expires_at", "expireAt")));
        if (StringUtils.isBlank(result.getSessionToken()) || StringUtils.isBlank(result.getWeworkUserid())) {
            throw new MiduoSsoException("米多校验响应缺少 sessionToken 或 wework_userid");
        }
        return result;
    }

    public MiduoRefreshResult refreshSessionToken(String sessionToken) {
        ensureEnabled();
        if (StringUtils.isBlank(sessionToken)) {
            throw new MiduoSsoException("sessionToken 不能为空");
        }
        Map<String, Object> data = postSigned(PATH_REFRESH, sessionToken, Map.of("sessionToken", sessionToken));
        MiduoRefreshResult result = new MiduoRefreshResult();
        result.setSuccess(Boolean.TRUE.equals(asBoolean(data.get("success"))));
        if (!result.isSuccess()) {
            String msg = asString(data.get("message"));
            throw new MiduoSsoException(StringUtils.defaultIfBlank(msg, "米多 session 刷新失败"));
        }
        result.setSessionToken(StringUtils.defaultIfBlank(
                asString(firstNonBlank(data, "sessionToken", "session_token")), sessionToken));
        result.setExpiresAt(asEpochMillis(firstNonBlank(data,
                "sessionExpiresAt", "session_expires_at", "expiresAt", "expires_at", "expireAt")));
        return result;
    }

    public void revokeSessionToken(String sessionToken) {
        ensureEnabled();
        if (StringUtils.isBlank(sessionToken)) {
            return;
        }
        Map<String, Object> data = postSigned(PATH_REVOKE, sessionToken, Map.of("sessionToken", sessionToken));
        if (!Boolean.TRUE.equals(asBoolean(data.get("success")))) {
            log.warn("miduo revoke not success, token={} msg={}",
                    MiduoSsoLogUtils.maskToken(sessionToken), asString(data.get("message")));
        }
    }

    public String buildBridgeUrl() {
        return buildBridgeUrl(null);
    }

    /**
     * 登录桥：浏览器直接跳转（无需开放签名）。
     * redirectUri 须 HTTPS 且命中米多白名单；state 由本系统预先写入 Redis。
     */
    public String buildBridgeUrl(String state) {
        ensureEnabled();
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(trimSlash(properties.getBaseUrl()) + PATH_BRIDGE)
                .queryParam("appCode", properties.getAppCode())
                .queryParam("redirectUri", properties.getRedirectUri());
        if (StringUtils.isNotBlank(properties.getShortcutId())) {
            builder.queryParam("shortcutId", properties.getShortcutId());
        }
        if (StringUtils.isNotBlank(state)) {
            builder.queryParam("state", state);
        }
        return builder.build(true).toUriString();
    }

    /**
     * 签名：canonical = appCode + "\\n" + timestamp(秒) + "\\n" + nonce + "\\n" + signedValue
     */
    String sign(String signedValue, String timestampSeconds, String nonce) {
        try {
            String canonical = properties.getAppCode() + "\n" + timestampSeconds + "\n" + nonce + "\n" + signedValue;
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(properties.getAppSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new MiduoSsoException("HMAC 签名失败");
        }
    }

    private Map<String, Object> postSigned(String path, String signedValue, Map<String, Object> body) {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        rememberNonce(nonce);
        // 米多 OpenSsoRequestVerifier：Unix 秒，±300s
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String signature = sign(signedValue, timestamp, nonce);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-App-Code", properties.getAppCode());
        headers.set("X-App-Timestamp", timestamp);
        headers.set("X-App-Nonce", nonce);
        headers.set("X-App-Signature", signature);

        String url = trimSlash(properties.getBaseUrl()) + path;
        try {
            ResponseEntity<String> entity = restTemplate.postForEntity(
                    url, new HttpEntity<>(JSON.toJSONString(body), headers), String.class);
            if (!entity.getStatusCode().is2xxSuccessful() || StringUtils.isBlank(entity.getBody())) {
                throw new MiduoSsoException("米多开放 API 调用失败");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = JSON.parseObject(entity.getBody(), Map.class);
            if (resp == null) {
                throw new MiduoSsoException("米多开放 API 响应为空");
            }
            assertReturnCodeOk(resp, path);
            return asMap(resp.get("return_data"));
        } catch (MiduoSsoException e) {
            throw e;
        } catch (Exception e) {
            log.warn("miduo open api error path={} msg={}", path, e.getMessage());
            throw new MiduoSsoException("米多开放 API 网络异常");
        }
    }

    /**
     * 包装层：return_code==0 才继续读 return_data；业务成功仍看 valid/success。
     */
    private void assertReturnCodeOk(Map<String, Object> resp, String path) {
        Object codeObj = firstNonBlank(resp, "return_code", "returnCode", "code");
        if (codeObj == null) {
            // 部分环境可能直接返回 data；兼容无包装时跳过
            if (resp.containsKey("return_data") || resp.containsKey("valid") || resp.containsKey("success")) {
                return;
            }
            log.warn("miduo response missing return_code path={}", path);
            return;
        }
        long code;
        try {
            code = Long.parseLong(String.valueOf(codeObj));
        } catch (NumberFormatException e) {
            throw new MiduoSsoException("米多开放 API 返回码异常");
        }
        if (code != 0L && code != 200L) {
            String msg = asString(firstNonBlank(resp, "return_msg", "returnMsg", "message"));
            log.warn("miduo return_code={} path={} msg={}", code, path, msg);
            throw new MiduoSsoException(StringUtils.defaultIfBlank(msg, "米多开放 API 业务失败"));
        }
    }

    private void rememberNonce(String nonce) {
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(NONCE_KEY_PREFIX + nonce, "1", Duration.ofSeconds(300));
        if (Boolean.FALSE.equals(ok)) {
            throw new MiduoSsoException("nonce 重复，拒绝请求");
        }
    }

    private void ensureEnabled() {
        if (!properties.isConfigured()) {
            throw new MiduoSsoException("米多 SSO 未启用或配置不完整");
        }
    }

    private static String trimSlash(String url) {
        if (StringUtils.isBlank(url)) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }

    private static Object firstNonBlank(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object v = data.get(key);
            if (v != null && StringUtils.isNotBlank(String.valueOf(v))) {
                return v;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 统一为 Unix 毫秒。若值看起来像秒（&lt; 1e12），则乘 1000。
     */
    static Long asEpochMillis(Object value) {
        if (value == null) {
            return null;
        }
        long n;
        if (value instanceof Number num) {
            n = num.longValue();
        } else {
            try {
                n = Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        // Unix 秒约 1e9～1e10；毫秒约 1e12～1e13
        if (n > 0 && n < 1_000_000_000_000L) {
            return n * 1000L;
        }
        return n;
    }
}
