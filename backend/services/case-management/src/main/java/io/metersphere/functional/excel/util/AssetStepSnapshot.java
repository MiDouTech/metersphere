package io.metersphere.functional.excel.util;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import java.util.*;

/** Content-only transport in Excel notes. Never supplies case identity or permissions. */
public final class AssetStepSnapshot {
    public static final String PREFIX = "MSP-ASSET-STEPS/1\n";
    private AssetStepSnapshot() {}
    public static boolean encoded(String value) { return value != null && value.startsWith(PREFIX); }
    public static String encode(List<String> descriptions, List<String> results, List<Object> numbers, String expected) {
        return PREFIX + CaseStepSplitUtils.encodeExportSteps(List.of(JSON.toJSONString(
                Map.of("descriptions", descriptions, "results", results, "numbers", numbers, "expected", expected))));
    }
    public static Map<String,Object> decode(String value) {
        try {
            var parts = CaseStepSplitUtils.splitCell(value.substring(PREFIX.length()));
            if (parts.size()!=1) throw new IllegalArgumentException();
            Map<String,Object> data=JSON.parseObject(parts.getFirst(), Map.class);
            var descriptions=(List<?>)data.get("descriptions");var results=(List<?>)data.get("results");var numbers=(List<?>)data.get("numbers");
            if (descriptions.size()>10000 || descriptions.size()!=results.size() || descriptions.size()!=numbers.size()
                    || !(data.get("expected") instanceof String)
                    || descriptions.stream().anyMatch(x->!(x instanceof String)) || results.stream().anyMatch(x->!(x instanceof String))
                    || numbers.stream().anyMatch(x->x!=null && !(x instanceof Number))) throw new IllegalArgumentException();
            return data;
        } catch (RuntimeException invalid) { throw new MSException("资产步骤元数据无效，请重新导出后重试"); }
    }
    @SuppressWarnings("unchecked")
    public static List<String> parts(String value, String key) { return (List<String>)decode(value).get(key); }
    public static String visible(String value, String key) {
        var parts=parts(value,key);StringBuilder result=new StringBuilder();
        for(int i=0;i<parts.size();i++)result.append('[').append(i+1).append(']').append(parts.get(i)).append('\n');
        return result.toString();
    }
}
