package io.metersphere.system.notice.utils;


import io.metersphere.sdk.util.JSON;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class LarkClient {

    public static void send(String webhook, String context) {
        Map<String, Object> mp = new LinkedHashMap<>();
        Map<String, String> js = new HashMap<>();
        js.put("text", context);
        mp.put("msg_type", "text");
        mp.put("content", js);
        ClientPost.executeJson(webhook, JSON.toJSONString(mp));
    }
}
