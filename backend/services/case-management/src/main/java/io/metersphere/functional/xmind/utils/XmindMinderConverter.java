package io.metersphere.functional.xmind.utils;

import io.metersphere.functional.xmind.pojo.Attached;
import io.metersphere.functional.xmind.pojo.Children;
import io.metersphere.functional.xmind.pojo.JsonRootBean;
import io.metersphere.functional.xmind.pojo.RootTopic;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 将 XMind 解析树转为 kityminder MinderJson（只读预览用，不入库功能用例）
 */
public final class XmindMinderConverter {

    private XmindMinderConverter() {
    }

    public static Map<String, Object> toMinderJson(List<JsonRootBean> sheets) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> rootData = new LinkedHashMap<>();
        rootData.put("id", "root");
        rootData.put("text", "Xmind");
        rootData.put("expandState", "expand");
        root.put("data", rootData);

        List<Map<String, Object>> children = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(sheets)) {
            for (JsonRootBean sheet : sheets) {
                if (sheet == null) {
                    continue;
                }
                RootTopic topic = sheet.getRootTopic();
                if (topic == null) {
                    Map<String, Object> sheetNode = leaf(StringUtils.defaultIfBlank(sheet.getTitle(), "Sheet"));
                    children.add(sheetNode);
                    continue;
                }
                children.add(fromRootTopic(topic, sheet.getTitle()));
            }
        }
        root.put("children", children);

        Map<String, Object> minder = new LinkedHashMap<>();
        minder.put("root", root);
        minder.put("template", "default");
        minder.put("treePath", new ArrayList<>());
        return minder;
    }

    private static Map<String, Object> fromRootTopic(RootTopic topic, String sheetTitle) {
        Map<String, Object> node = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", StringUtils.defaultIfBlank(topic.getId(), UUID.randomUUID().toString()));
        String text = StringUtils.defaultIfBlank(topic.getTitle(), StringUtils.defaultIfBlank(sheetTitle, "Root"));
        data.put("text", text);
        data.put("expandState", "expand");
        node.put("data", data);
        node.put("children", fromChildren(topic.getChildren()));
        return node;
    }

    private static List<Map<String, Object>> fromChildren(Children children) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (children == null || CollectionUtils.isEmpty(children.getAttached())) {
            return list;
        }
        for (Attached attached : children.getAttached()) {
            list.add(fromAttached(attached));
        }
        return list;
    }

    private static Map<String, Object> fromAttached(Attached attached) {
        Map<String, Object> node = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", StringUtils.defaultIfBlank(attached.getId(), UUID.randomUUID().toString()));
        data.put("text", StringUtils.defaultIfBlank(attached.getTitle(), ""));
        data.put("expandState", "expand");
        node.put("data", data);
        node.put("children", fromChildren(attached.getChildren()));
        return node;
    }

    private static Map<String, Object> leaf(String text) {
        Map<String, Object> node = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", UUID.randomUUID().toString());
        data.put("text", text);
        data.put("expandState", "expand");
        node.put("data", data);
        node.put("children", new ArrayList<>());
        return node;
    }
}
