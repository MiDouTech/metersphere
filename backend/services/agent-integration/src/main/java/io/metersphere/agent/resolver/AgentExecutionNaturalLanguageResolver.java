package io.metersphere.agent.resolver;

import io.metersphere.agent.dto.AgentSearchFilters;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative natural-language-to-filter resolver.
 * It emits only whitelisted filter values and never emits SQL or executable expressions.
 * An AI provider may populate the same DTO later, but server-side validation remains authoritative.
 */
@Component
public class AgentExecutionNaturalLanguageResolver {
    private static final Pattern PRIORITY = Pattern.compile("(?i)(?:^|[^a-z0-9])(P[0-4])(?:$|[^a-z0-9])");
    private static final Pattern LIMIT = Pattern.compile("(?:前|最多|限制)?\\s*(\\d{1,3})\\s*(?:条|个|cases?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("#([\\p{L}\\p{N}_-]{1,32})");
    private static final List<String> RISK_WORDS = List.of("删除", "支付", "退款", "发布", "权限", "批量修改", "转账", "清空");

    public Resolution resolve(String query, AgentSearchFilters explicit) {
        AgentSearchFilters result = copy(explicit);
        List<String> matched = new ArrayList<>();
        if (StringUtils.isBlank(query)) {
            return new Resolution(result, hasFilter(result), hasFilter(result) ? 1.0D : 0D, matched);
        }
        if (isSuspiciousExpression(query)) {
            return new Resolution(result, hasFilter(result), hasFilter(result) ? 1.0D : 0D, matched);
        }

        Set<String> priorities = new LinkedHashSet<>(safe(result.getPriority()));
        Matcher priorityMatcher = PRIORITY.matcher(query);
        while (priorityMatcher.find()) {
            priorities.add(priorityMatcher.group(1).toUpperCase(Locale.ROOT));
        }
        if (!priorities.isEmpty()) {
            result.setPriority(new ArrayList<>(priorities));
            matched.add("priority");
        }

        Set<String> lastResults = new LinkedHashSet<>(safe(result.getLastExecuteResult()));
        if (containsAny(query, "最近失败", "上次失败", "失败用例", "执行失败")) {
            lastResults.add("FAILED");
        }
        if (query.contains("阻塞")) {
            lastResults.add("BLOCKED");
        }
        if (containsAny(query, "未执行", "没有执行")) {
            lastResults.add("PENDING");
        }
        if (containsAny(query, "最近通过", "上次通过", "成功用例")) {
            lastResults.add("SUCCESS");
        }
        if (!lastResults.isEmpty()) {
            result.setLastExecuteResult(new ArrayList<>(lastResults));
            matched.add("lastExecuteResult");
        }

        Set<String> tags = new LinkedHashSet<>(safe(result.getTags()));
        Matcher tagMatcher = TAG.matcher(query);
        while (tagMatcher.find()) {
            tags.add(tagMatcher.group(1));
        }
        if (query.contains("冒烟")) {
            tags.add("smoke");
        }
        if (!tags.isEmpty()) {
            result.setTags(new ArrayList<>(tags));
            matched.add("tags");
        }

        if (Boolean.TRUE.equals(result.getExcludeRiskActions()) || isRiskExcluded(query)) {
            result.setExcludeRiskActions(true);
            matched.add("excludeRiskActions");
        }

        Matcher limitMatcher = LIMIT.matcher(query);
        if (limitMatcher.find()) {
            result.setLimit(Math.min(Integer.parseInt(limitMatcher.group(1)), 100));
            matched.add("limit");
        } else if (result.getLimit() != null) {
            result.setLimit(Math.min(Math.max(result.getLimit(), 1), 100));
        }

        if (StringUtils.isBlank(result.getKeyword())) {
            String keyword = extractKeyword(query);
            if (StringUtils.isNotBlank(keyword)) {
                result.setKeyword(keyword);
                matched.add("keyword");
            }
        }
        boolean recognized = hasFilter(result) || !matched.isEmpty();
        double confidence = recognized ? Math.min(0.55D + matched.size() * 0.08D, 0.95D) : 0D;
        return new Resolution(result, recognized, confidence, matched);
    }

    public boolean containsRiskWord(String text) {
        return StringUtils.isNotBlank(text) && RISK_WORDS.stream().anyMatch(text::contains);
    }

    private boolean isRiskExcluded(String query) {
        return containsAny(query, "排除删除", "不要删除", "不执行删除", "排除支付", "不要支付", "排除高风险", "仅低风险");
    }

    private boolean isSuspiciousExpression(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        return lower.contains(";") || lower.contains("--") || lower.contains("/*")
                || lower.matches(".*\\b(drop|truncate|alter|insert|select|update)\\b.*");
    }

    private String extractKeyword(String query) {
        String normalized = query
                .replaceAll("(?i)(?:^|[^a-z0-9])P[0-4](?:$|[^a-z0-9])", " ")
                .replaceAll("#([\\p{L}\\p{N}_-]{1,32})", " ")
                .replaceAll("(?:前|最多|限制)?\\s*\\d{1,3}\\s*(?:条|个|cases?)", " ")
                .replaceAll("最近失败|上次失败|失败用例|执行失败|最近通过|上次通过|成功用例|未执行|没有执行|阻塞", " ")
                .replaceAll("冒烟", " ")
                .replaceAll("排除删除|不要删除|不执行删除|排除支付|不要支付|排除高风险|仅低风险", " ")
                .replaceAll("请|帮我|执行|运行|测试|相关|全部|所有|用例|模块|中的|中|的", " ")
                .replaceAll("[，。；、,:：]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.length(normalized) > 64 ? normalized.substring(0, 64) : normalized;
    }

    private boolean hasFilter(AgentSearchFilters filter) {
        return filter != null && (StringUtils.isNotBlank(filter.getKeyword())
                || CollectionUtils.isNotEmpty(filter.getPriority())
                || CollectionUtils.isNotEmpty(filter.getLastExecuteResult())
                || CollectionUtils.isNotEmpty(filter.getTags())
                || CollectionUtils.isNotEmpty(filter.getModuleIds())
                || Boolean.TRUE.equals(filter.getExcludeRiskActions()));
    }

    private AgentSearchFilters copy(AgentSearchFilters source) {
        AgentSearchFilters target = new AgentSearchFilters();
        if (source == null) {
            return target;
        }
        target.setKeyword(StringUtils.trimToNull(source.getKeyword()));
        target.setPriority(new ArrayList<>(safe(source.getPriority())));
        target.setLastExecuteResult(new ArrayList<>(safe(source.getLastExecuteResult())));
        target.setTags(new ArrayList<>(safe(source.getTags())));
        target.setModuleIds(new ArrayList<>(safe(source.getModuleIds())));
        target.setExcludeRiskActions(source.getExcludeRiskActions());
        target.setLimit(source.getLimit());
        return target;
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream().filter(StringUtils::isNotBlank).map(String::trim).toList();
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public record Resolution(AgentSearchFilters filters, boolean recognized, double confidence,
                             List<String> matchedReasons) {
    }
}
