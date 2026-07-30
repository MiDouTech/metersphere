package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "MCP 包清单")
public class AgentMcpManifestDTO {
    private String name;
    private String version;
    private String fileName;
    private String description;
    private String nodeEngine;
    private String installHint;
    private boolean available;
}
