package io.metersphere.system.service.wecom;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.wecom.WecomDepartmentDTO;
import io.metersphere.system.dto.wecom.WecomDepartmentListResponse;
import io.metersphere.system.dto.wecom.WecomTokenResponse;
import io.metersphere.system.dto.wecom.WecomUserDTO;
import io.metersphere.system.dto.wecom.WecomUserDetailResponse;
import io.metersphere.system.dto.wecom.WecomUserListResponse;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
public class WecomContactClient {

    private static final int TOKEN_REFRESH_BUFFER_SECONDS = 300;

    @Value("${wecom.api-base-url:https://qyapi.weixin.qq.com}")
    private String apiBaseUrl;

    @Value("${wecom.contact.retry-times:3}")
    private int retryTimes;

    @Value("${wecom.contact.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${wecom.contact.read-timeout-ms:10000}")
    private int readTimeoutMs;

    private RestTemplate restTemplate;

    private final ConcurrentHashMap<String, TokenCacheEntry> tokenCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> tokenLocks = new ConcurrentHashMap<>();

    @PostConstruct
    void initRestTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setConnectionRequestTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        restTemplate = new RestTemplate(factory);
    }

    public String getAccessToken(String corpId, String contactSecret) {
        validateCredential(corpId, contactSecret);
        String cacheKey = buildCacheKey(corpId, contactSecret);
        TokenCacheEntry cached = tokenCache.get(cacheKey);
        if (cached != null && !cached.isExpiringSoon()) {
            return cached.accessToken();
        }
        synchronized (getLock(cacheKey)) {
            cached = tokenCache.get(cacheKey);
            if (cached != null && !cached.isExpiringSoon()) {
                return cached.accessToken();
            }
            WecomTokenResponse response = fetchAccessToken(corpId, contactSecret);
            long expireAt = System.currentTimeMillis() + response.getExpiresIn() * 1000L;
            tokenCache.put(cacheKey, new TokenCacheEntry(response.getAccessToken(), expireAt));
            return response.getAccessToken();
        }
    }

    public void invalidateAccessToken(String corpId, String contactSecret) {
        tokenCache.remove(buildCacheKey(corpId, contactSecret));
    }

    public List<WecomDepartmentDTO> listDepartments(String accessToken) {
        validateAccessToken(accessToken);
        String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/cgi-bin/department/list")
                .queryParam("access_token", accessToken)
                .toUriString();
        WecomDepartmentListResponse response = getWithRetry(url, WecomDepartmentListResponse.class);
        checkErrcode(response.getErrcode(), response.getErrmsg());
        return response.getDepartment() == null ? Collections.emptyList() : response.getDepartment();
    }

    public List<WecomUserDTO> listDepartmentUsers(String accessToken, long deptId, boolean fetchChild) {
        validateAccessToken(accessToken);
        String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/cgi-bin/user/list")
                .queryParam("access_token", accessToken)
                .queryParam("department_id", deptId)
                .queryParam("fetch_child", fetchChild ? 1 : 0)
                .toUriString();
        WecomUserListResponse response = getWithRetry(url, WecomUserListResponse.class);
        checkErrcode(response.getErrcode(), response.getErrmsg());
        return response.getUserList() == null ? Collections.emptyList() : response.getUserList();
    }

    public WecomUserDTO getUser(String accessToken, String userId) {
        validateAccessToken(accessToken);
        if (StringUtils.isBlank(userId)) {
            throw new MSException("wecom userId is required");
        }
        String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/cgi-bin/user/get")
                .queryParam("access_token", accessToken)
                .queryParam("userid", userId)
                .toUriString();
        WecomUserDetailResponse response = getWithRetry(url, WecomUserDetailResponse.class);
        checkErrcode(response.getErrcode(), response.getErrmsg());
        return response;
    }

    public <T> T executeWithToken(String corpId, String contactSecret, Function<String, T> action) {
        String token = getAccessToken(corpId, contactSecret);
        try {
            return action.apply(token);
        } catch (WecomApiException e) {
            if (e.isTokenExpired()) {
                invalidateAccessToken(corpId, contactSecret);
                token = getAccessToken(corpId, contactSecret);
                return action.apply(token);
            }
            throw e;
        }
    }

    private WecomTokenResponse fetchAccessToken(String corpId, String contactSecret) {
        String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/cgi-bin/gettoken")
                .queryParam("corpid", corpId)
                .queryParam("corpsecret", contactSecret)
                .toUriString();
        WecomTokenResponse response = getWithRetry(url, WecomTokenResponse.class);
        checkErrcode(response.getErrcode(), response.getErrmsg());
        if (StringUtils.isBlank(response.getAccessToken()) || response.getExpiresIn() == null) {
            throw new MSException("WeCom gettoken response missing access_token or expires_in");
        }
        return response;
    }

    private <T> T getWithRetry(String url, Class<T> responseType) {
        Exception lastException = null;
        for (int attempt = 0; attempt < retryTimes; attempt++) {
            try {
                if (attempt > 0) {
                    Thread.sleep(500L * (1L << (attempt - 1)));
                }
                return restTemplate.getForObject(url, responseType);
            } catch (ResourceAccessException e) {
                lastException = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MSException("WeCom API request interrupted");
            }
        }
        throw new MSException("WeCom API request failed after retries: " + lastException.getMessage());
    }

    private void checkErrcode(Integer errcode, String errmsg) {
        if (errcode == null || errcode == 0) {
            return;
        }
        throw new WecomApiException(errcode, errmsg);
    }

    private void validateCredential(String corpId, String contactSecret) {
        if (StringUtils.isAnyBlank(corpId, contactSecret)) {
            throw new MSException("corpId and contactSecret are required");
        }
    }

    private void validateAccessToken(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            throw new MSException("accessToken is required");
        }
    }

    private String buildCacheKey(String corpId, String contactSecret) {
        return corpId + "\0" + contactSecret;
    }

    private Object getLock(String cacheKey) {
        return tokenLocks.computeIfAbsent(cacheKey, key -> new Object());
    }

    private record TokenCacheEntry(String accessToken, long expireAt) {
        boolean isExpiringSoon() {
            return System.currentTimeMillis() >= expireAt - TOKEN_REFRESH_BUFFER_SECONDS * 1000L;
        }
    }
}
