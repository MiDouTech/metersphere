package io.metersphere.system.notice.utils;


import io.metersphere.sdk.util.JSON;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DingClient {

    public static void send(String webhook, String context, List<String>mobileList) {
        Map<String, Object> mp = new LinkedHashMap<>();
        Map<String, Object> js = new HashMap<>();
        Map<String, Object> at = new HashMap<>();
        js.put("content", context);
        at.put("atMobiles",mobileList);
        at.put("isAtAll",false);
        mp.put("msgtype", "text");
        mp.put("text", js);
        mp.put("at", at);
        ClientPost.executeJson(webhook, JSON.toJSONString(mp));
    }

}
