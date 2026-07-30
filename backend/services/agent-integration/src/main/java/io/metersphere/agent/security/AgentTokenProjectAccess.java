package io.metersphere.agent.security;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.AgentToken;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent Token 可访问项目白名单：project_ids 为空表示全部项目。
 */
public final class AgentTokenProjectAccess {
    private AgentTokenProjectAccess() {
    }

    public static List<String> parseProjectIds(AgentToken token) {
        if (token == null) {
            return Collections.emptyList();
        }
        if (StringUtils.isNotBlank(token.getProjectIds())) {
            try {
                List<String> list = JSON.parseArray(token.getProjectIds(), String.class);
                if (list == null) {
                    return Collections.emptyList();
                }
                List<String> cleaned = new ArrayList<>();
                for (String id : list) {
                    if (StringUtils.isNotBlank(id)) {
                        cleaned.add(id.trim());
                    }
                }
                return cleaned;
            } catch (Exception ignored) {
                // fall through to legacy project_id
            }
        }
        if (StringUtils.isNotBlank(token.getProjectId())) {
            return List.of(token.getProjectId().trim());
        }
        return Collections.emptyList();
    }

    /** 空白名单 = 全部项目 */
    public static boolean allowsAll(AgentToken token) {
        return CollectionUtils.isEmpty(parseProjectIds(token));
    }

    public static boolean allows(AgentToken token, String projectId) {
        if (StringUtils.isBlank(projectId)) {
            return true;
        }
        List<String> allowed = parseProjectIds(token);
        if (CollectionUtils.isEmpty(allowed)) {
            return true;
        }
        return allowed.contains(projectId);
    }

    public static String toStorageJson(List<String> projectIds) {
        if (CollectionUtils.isEmpty(projectIds)) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String id : projectIds) {
            if (StringUtils.isNotBlank(id)) {
                cleaned.add(id.trim());
            }
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(cleaned);
    }

    public static String primaryProjectId(List<String> projectIds) {
        if (CollectionUtils.isEmpty(projectIds)) {
            return null;
        }
        for (String id : projectIds) {
            if (StringUtils.isNotBlank(id)) {
                return id.trim();
            }
        }
        return null;
    }
}
