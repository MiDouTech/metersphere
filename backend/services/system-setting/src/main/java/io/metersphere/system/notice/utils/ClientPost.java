package io.metersphere.system.notice.utils;

import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.LogUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.util.Map;

public class ClientPost {

    public static void executeClient(String webhook, CloseableHttpClient httpClient,  Map<String, Object> mp) {
        executeRaw(webhook, httpClient, JSON.toJSONString(mp));
    }

    /** Owns and closes the newly created client. Supplied clients in executeClient remain caller-owned. */
    public static void executeJson(String webhook, String body) {
        executeOwned(webhook, HttpClients.createDefault(), body);
    }

    static void executeOwned(String webhook, CloseableHttpClient client, String body) {
        try (client) {
            executeRaw(webhook, client, body);
        } catch (Exception unavailable) {
            LogUtils.error("Notification HTTP client close failed");
        }
    }

    static void executeRaw(String webhook, CloseableHttpClient httpClient, String body) {
        try {
            // 创建Http Post请求
            HttpPost httpPost = new HttpPost(webhook);
            // 创建请求内容
            StringEntity entity = new StringEntity(body, ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);
            // 执行http请求
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int status = response.getCode();
                if (status < 200 || status >= 300) LogUtils.error("Notification HTTP non-2xx status: " + status);
            }
        } catch (Exception unavailable) {
            // Preserve legacy best-effort behavior without leaking private URLs via exception messages.
            LogUtils.error("Notification HTTP attempt failed");
        }
    }

}
