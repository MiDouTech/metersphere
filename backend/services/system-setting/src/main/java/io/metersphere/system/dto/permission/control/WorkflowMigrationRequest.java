package io.metersphere.system.dto.permission.control;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@Data
public class WorkflowMigrationRequest {
    @NotBlank
    private String targetFlowId;
    private boolean dryRun = true;
    private Map<String, String> statusMappings = new LinkedHashMap<>();
    @NotEmpty(message = "请选择需要关联的历史缺陷")
    private List<String> bugIds;
    private List<String> projectIds;
}
