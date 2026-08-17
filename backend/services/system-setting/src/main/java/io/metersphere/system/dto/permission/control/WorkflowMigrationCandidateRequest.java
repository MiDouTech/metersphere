package io.metersphere.system.dto.permission.control;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkflowMigrationCandidateRequest {
    private int current = 1;
    private int pageSize = 20;
    private String keyword;
    private List<String> projectIds = new ArrayList<>();
    private List<String> sourceStatusIds = new ArrayList<>();
    private Long createTimeStart;
    private Long createTimeEnd;
}
