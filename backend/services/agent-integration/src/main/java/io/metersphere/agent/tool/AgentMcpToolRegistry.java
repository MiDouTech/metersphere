package io.metersphere.agent.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 统一 MCP Tool 注册中心：内置与扩展 Handler 同源，供 tools/list 与 tools/call 使用。
 */
@Component
public class AgentMcpToolRegistry {
    private final Map<String, AgentMcpToolHandler> handlers = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();

    public AgentMcpToolRegistry(List<AgentMcpToolHandler> toolHandlers) {
        if (toolHandlers != null) {
            for (AgentMcpToolHandler handler : toolHandlers) {
                register(handler);
            }
        }
        // 兼容别名：方案名 search_projects → 正式名 metersphere.project.search
        aliases.put("search_projects", "metersphere.project.search");
    }

    public void register(AgentMcpToolHandler handler) {
        if (handler == null || StringUtils.isBlank(handler.name())) {
            return;
        }
        handlers.put(handler.name(), handler);
    }

    public Optional<AgentMcpToolHandler> find(String name) {
        if (StringUtils.isBlank(name)) {
            return Optional.empty();
        }
        AgentMcpToolHandler handler = handlers.get(name);
        if (handler != null) {
            return Optional.of(handler);
        }
        String canonical = aliases.get(name);
        if (canonical != null) {
            return Optional.ofNullable(handlers.get(canonical));
        }
        return Optional.empty();
    }

    public Collection<AgentMcpToolHandler> all() {
        return handlers.values();
    }

    public List<String> names() {
        return new ArrayList<>(handlers.keySet());
    }

    public boolean isWriteTool(String name) {
        return find(name)
                .map(handler -> !Boolean.TRUE.equals(handler.annotations().get("readOnlyHint")))
                .orElse(false);
    }
}
