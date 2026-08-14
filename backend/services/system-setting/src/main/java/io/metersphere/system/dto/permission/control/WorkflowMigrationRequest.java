package io.metersphere.system.dto.permission.control;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class WorkflowMigrationRequest {
    @NotBlank
    private String targetFlowId;
    private boolean dryRun = true;
    private Map<String, String> statusMappings = new LinkedHashMap<>();
}
