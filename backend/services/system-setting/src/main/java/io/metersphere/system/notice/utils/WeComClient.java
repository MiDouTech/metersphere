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
     * 企业微信群机器人文本消息，支持手机号 / 企微 userid @提醒
     */
    public static void send(String webhook, String context, List<String> mobileList, List<String> mentionedUserIds) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        Map<String, Object> mp = new LinkedHashMap<>();
        Map<String, Object> js = new HashMap<>();
        js.put("content", context);
        if (mobileList != null && !mobileList.isEmpty()) {
            js.put("mentioned_mobile_list", mobileList);
        }
        if (mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            js.put("mentioned_list", mentionedUserIds);
        }
        mp.put("msgtype", "text");
        mp.put("text", js);
        ClientPost.executeClient(webhook, httpClient, mp);
    }

}
