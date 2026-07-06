package io.metersphere.system.dto.department;

import lombok.Data;

@Data
public class OrgWecomSyncStatusDTO {
    private String organizationId;
    private Long lastSyncTime;
    private String syncLogId;
    private String syncMode;
    private String syncStatus;
    private Integer deptTotal;
    private Integer deptSuccess;
    private Integer deptFailed;
    private Integer userTotal;
    private Integer userSuccess;
    private Integer userFailed;
    private Long durationMs;
    private String errorMessage;
    private Long logCreateTime;
}
