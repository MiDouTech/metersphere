package io.metersphere.system.dto.department;

import lombok.Data;

@Data
public class OrgWecomSyncManualResponse {
    private String syncLogId;
    private String syncStatus;
    private Integer deptSuccess;
    private Integer deptFailed;
    private Integer userSuccess;
    private Integer userFailed;
    private Long durationMs;
    private String errorMessage;
}
