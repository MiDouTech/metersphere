package io.metersphere.agent.security;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 Token scopes 字符串解析为精确集合（兼容 , ; 空白分隔，以及 JSON 数组字面量）。
 */
public final class AgentTokenScopeParser {

    private AgentTokenScopeParser() {
    }

    public static Set<String> parse(String scopes) {
        if (StringUtils.isBlank(scopes)) {
            return Collections.emptySet();
        }
        String raw = StringUtils.trim(scopes);
        if (raw.startsWith("[") && raw.endsWith("]")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .map(scope -> scope.replace("\"", "").replace("'", ""))
                .map(scope -> scope.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 创建/更新 Token 时校验：非空、全部为白名单枚举。
     */
    public static String normalizeAndValidate(String scopes) {
        Set<String> parsed = parse(scopes);
        if (parsed.isEmpty()) {
            throw new MSException("Agent Token scopes 不能为空");
        }
        for (String scope : parsed) {
            if (!AgentTokenScope.isKnownScope(scope)) {
                throw new MSException("未知 Agent Token scope: " + scope);
            }
        }
        return String.join(";", parsed);
    }

    public static boolean hasScope(Set<String> owned, String requiredScope) {
        if (owned == null || owned.isEmpty() || StringUtils.isBlank(requiredScope)) {
            return false;
        }
        String required = requiredScope.trim().toUpperCase(Locale.ROOT);
        if (owned.contains(AgentTokenScope.AGENT_ALL)) {
            return true;
        }
        if (owned.contains(required)) {
            return true;
        }
        if (AgentTokenScope.isFunctionalScope(required) && owned.contains(AgentTokenScope.FUNCTIONAL_ALL)) {
            return true;
        }
        // BUG_WRITE 覆盖 BUG_READ
        if (AgentTokenScope.BUG_READ.equals(required) && owned.contains(AgentTokenScope.BUG_WRITE)) {
            return true;
        }
        // 迁移期：FUNCTIONAL_READ / FUNCTIONAL_ALL 临时覆盖 PROJECT_READ
        if (AgentTokenScope.PROJECT_READ.equals(required)
                && (owned.contains(AgentTokenScope.FUNCTIONAL_READ)
                || owned.contains(AgentTokenScope.FUNCTIONAL_ALL))) {
            return true;
        }
        return false;
    }

    public static boolean hasScope(String scopes, String requiredScope) {
        return hasScope(parse(scopes), requiredScope);
    }
}
