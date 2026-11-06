package io.metersphere.agent.tool;

import io.metersphere.agent.service.AgentBugImageService;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.sdk.exception.MSException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "agent.bug-images.enabled", havingValue = "true", matchIfMissing = true)
public class BugImageReadTool implements AgentMcpToolHandler {
    private final AgentBugImageService images;
    public BugImageReadTool(AgentBugImageService images) { this.images = images; }
    public String name() { return "metersphere.bug.image.read"; }
    public String description() { return "Read one current saved defect image as original PNG/JPEG MCP image content (5 MiB, 20 million pixels). Refresh bug detail for imageRef."; }
    public String requiredScope() { return AgentTokenScope.BUG_READ; }
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(Map.of("projectId", AgentMcpToolSchemas.string(1,128),
                "bugId", AgentMcpToolSchemas.string(1,128), "imageRef", AgentMcpToolSchemas.string(133,133)), List.of("projectId", "bugId", "imageRef"));
    }
    public Map<String, Object> annotations() { return AgentMcpToolSchemas.annotations(requiredScope(), true, false, true); }
    public Object execute(Map<String, Object> args) {
        if (!args.keySet().equals(java.util.Set.of("projectId", "bugId", "imageRef"))) throw new MSException("BUG_IMAGE_INVALID_ARGUMENT");
        return images.read(text(args,"projectId"), text(args,"bugId"), text(args,"imageRef"));
    }
    private String text(Map<String, Object> args, String name) {
        if (!(args.get(name) instanceof String value) || value.isBlank()) throw new MSException("BUG_IMAGE_INVALID_ARGUMENT");
        return value;
    }
}
