package io.metersphere.functional.excel.util;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 功能用例导入：步骤描述智能拆分
 * <ul>
 *   <li>有序号且无层级 → 按该序号拆</li>
 *   <li>有序号且有层级 → 只按最高层拆，次级留在段内</li>
 *   <li>无序号 → 按换行拆</li>
 *   <li>行中编号 → 仍按有序号规则</li>
 * </ul>
 */
public final class CaseStepSplitUtils {

    /** 顶层：【1】 [1] 1. 1、 */
    private static final Pattern TOP_MARKER = Pattern.compile(
            "(【\\d+】|\\[\\d+]|\\d+[.、])"
    );
    /** 次级：①②… 或 (1)（1） */
    private static final Pattern SUB_MARKER = Pattern.compile(
            "[①②③④⑤⑥⑦⑧⑨⑩]|[（(]\\d+[）)]"
    );

    private CaseStepSplitUtils() {
    }

    /**
     * 将单元格内容拆成多段步骤文本（保留段内次级编号原文）
     */
    public static List<String> splitCell(String cellContent) {
        List<String> result = new ArrayList<>();
        if (StringUtils.isBlank(cellContent)) {
            result.add(StringUtils.EMPTY);
            return result;
        }
        String content = normalizeHtmlBreaks(cellContent).trim();
        if (!TOP_MARKER.matcher(content).find()) {
            // 无顶层序号：按换行拆
            return splitByNewLine(content);
        }
        boolean hasHierarchy = TOP_MARKER.matcher(content).find() && SUB_MARKER.matcher(content).find();
        // 有/无层级：均按顶层标记拆；有层级时次级自然留在段内
        return splitByTopMarker(content, hasHierarchy);
    }

    private static List<String> splitByNewLine(String content) {
        List<String> result = new ArrayList<>();
        String[] lines = content.split("\\r?\\n", -1);
        for (String line : lines) {
            String trimmed = line.replaceAll("(?m)^\\s+|\\s+$", StringUtils.EMPTY);
            if (StringUtils.isNotEmpty(trimmed)) {
                result.add(trimmed);
            }
        }
        if (result.isEmpty()) {
            result.add(StringUtils.EMPTY);
        }
        return result;
    }

    private static List<String> splitByTopMarker(String content, boolean hasHierarchy) {
        List<String> result = new ArrayList<>();
        Matcher matcher = TOP_MARKER.matcher(content);
        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) {
            // 无层级时所有顶层标记都切；有层级时同样按顶层切（① 不会匹配 TOP_MARKER）
            starts.add(matcher.start());
        }
        if (starts.isEmpty()) {
            result.add(content.trim());
            return result;
        }
        // 若第一个标记前有前缀文字，并入第一段
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : content.length();
            String part = content.substring(start, end).replaceAll("(?m)^\\s+|\\s+$", StringUtils.EMPTY);
            if (i == 0 && start > 0) {
                String prefix = content.substring(0, start).replaceAll("(?m)^\\s+|\\s+$", StringUtils.EMPTY);
                if (StringUtils.isNotEmpty(prefix)) {
                    part = prefix + part;
                }
            }
            if (StringUtils.isNotEmpty(part)) {
                result.add(part);
            }
        }
        if (result.isEmpty()) {
            result.add(content.trim());
        }
        // hasHierarchy 当前算法已满足「只按最高层」；保留参数便于后续扩展
        return result;
    }

    private static String normalizeHtmlBreaks(String content) {
        return content
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)<p[^>]*>", StringUtils.EMPTY);
    }

    /**
     * 将步骤列表拼成 TEXT 全文（换行分隔）
     */
    public static String joinAsText(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return StringUtils.EMPTY;
        }
        return String.join("\n", parts);
    }
}
