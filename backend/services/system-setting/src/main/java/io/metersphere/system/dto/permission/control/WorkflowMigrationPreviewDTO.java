package io.metersphere.system.dto.permission.control;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class WorkflowMigrationPreviewDTO {
    private String targetFlowId;
    private Integer targetVersion;
    private long affectedBugCount;
    private Map<String, String> suggestedMappings = new LinkedHashMap<>();
    private List<String> unresolvedStatusIds = new ArrayList<>();
    private List<ProjectDifference> projects = new ArrayList<>();
    private List<Map<String, String>> targetStatuses = new ArrayList<>();

    @Data
    public static class ProjectDifference {
        private String projectId;
        private String projectName;
        private long bugCount;
        private List<String> sourceStatusIds = new ArrayList<>();
        private List<String> unresolvedStatusIds = new ArrayList<>();
    }
}
