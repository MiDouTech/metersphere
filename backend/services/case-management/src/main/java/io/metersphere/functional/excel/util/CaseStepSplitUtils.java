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

    private static final String FRAMED_PREFIX = "MSP-STEPS/1\n";
    private static final Pattern FRAME = Pattern.compile("\\[(\\d+):(\\d+)]");

    /** Length framing is used only when legacy numbering cannot preserve body whitespace/newlines. */
    public static String encodeExportSteps(List<String> parts) {
        StringBuilder encoded = new StringBuilder(FRAMED_PREFIX);
        for (int i = 0; i < parts.size(); i++) {
            String body = StringUtils.defaultString(parts.get(i));
            encoded.append('[').append(i + 1).append(':').append(body.length()).append(']').append(body).append('\n');
        }
        return encoded.append("MSP-STEPS-END").toString();
    }

    private static List<String> decodeExportSteps(String content) {
        List<String> parts = new ArrayList<>();
        int offset = FRAMED_PREFIX.length();
        while (offset < content.length() && !content.substring(offset).equals("MSP-STEPS-END")) {
            Matcher frame = FRAME.matcher(content);
            frame.region(offset, content.length());
            if (!frame.lookingAt() || !frame.group(1).equals(String.valueOf(parts.size() + 1)))
                throw new IllegalArgumentException("步骤编码无效，请重新导出或改为普通编号步骤");
            long length;
            try { length = Long.parseLong(frame.group(2)); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("步骤长度无效，请重新导出"); }
            if (length > content.length() - frame.end() - 1L)
                throw new IllegalArgumentException("步骤内容长度无效，请重新导出");
            int end = frame.end() + (int) length;
            if (content.charAt(end) != '\n')
                throw new IllegalArgumentException("步骤内容已改变，请重新导出或改为普通编号步骤");
            parts.add(content.substring(frame.end(), (int) end));
            offset = (int) end + 1;
        }
        if (!content.substring(offset).equals("MSP-STEPS-END"))
            throw new IllegalArgumentException("步骤编码结尾无效，请重新导出");
        return parts.isEmpty() ? List.of("") : parts;
    }

    /** 顶层：【1】 [1] 1. 1、 */
    private static final Pattern TOP_MARKER = Pattern.compile(
            "(【\\d+】|\\[\\d+]|(?<![\\d.])\\d+[.、](?!\\d))"
    );
    /** 次级：①②… 或 (1)（1） */
    private static final Pattern SUB_MARKER = Pattern.compile(
            "[①②③④⑤⑥⑦⑧⑨⑩]|[（(]\\d+[）)]"
    );

    private CaseStepSplitUtils() {
    }

    public static boolean requiresExportFraming(List<String> parts) {
        return parts.stream().anyMatch(part -> part != null && (part.contains("\n") || part.contains("\r")
                || !part.equals(normalizeHtmlBreaks(part)) || !part.equals(part.strip()) || TOP_MARKER.matcher(part).find() || part.startsWith("MSP-STEPS/1")));
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
        if (cellContent.startsWith(FRAMED_PREFIX)) return decodeExportSteps(cellContent);
        String content = normalizeHtmlBreaks(cellContent).trim();
        // Our single-row export uses consecutive [n] markers at line starts.
        // Decode those before the permissive legacy importer so decimal values,
        // multiline bodies and empty step positions survive a round trip.
        Matcher exported = Pattern.compile("(?m)^\\[(\\d+)]").matcher(content);
        List<Integer> bodyStarts = new ArrayList<>();
        List<Integer> markerStarts = new ArrayList<>();
        int expected = 1;
        boolean consecutive = true;
        while (exported.find()) {
            if (!exported.group(1).equals(String.valueOf(expected++))) consecutive = false;
            markerStarts.add(exported.start());
            bodyStarts.add(exported.end());
        }
        if (consecutive && !markerStarts.isEmpty() && markerStarts.getFirst() == 0
                && TOP_MARKER.matcher(content).results().count() == markerStarts.size()) {
            List<String> decoded = new ArrayList<>();
            for (int i = 0; i < bodyStarts.size(); i++) {
                int end = i + 1 < markerStarts.size() ? markerStarts.get(i + 1) : content.length();
                String body = content.substring(bodyStarts.get(i), end);
                if (i + 1 < markerStarts.size() && body.endsWith("\n")) body = body.substring(0, body.length() - 1);
                decoded.add(body);
            }
            return decoded;
        }
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
