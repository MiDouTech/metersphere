package io.metersphere.system.notice.utils;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WeComClient {

    public static void send(String webhook, String context, List<String> mobileList) {
        send(webhook, context, mobileList, null);
    }

    /**
     * 企业微信群机器人文本消息。
     * mentioned_list 使用企微 userid；正文可同时写入 @ 文本便于可读。不再依赖手机号。
     */
    public static void send(String webhook, String context, List<String> mobileList, List<String> mentionedUserIds) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        Map<String, Object> mp = new LinkedHashMap<>();
        Map<String, Object> js = new HashMap<>();
        js.put("content", context);
        if (mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            js.put("mentioned_list", mentionedUserIds);
        }
        mp.put("msgtype", "text");
        mp.put("text", js);
        ClientPost.executeClient(webhook, httpClient, mp);
    }

}
