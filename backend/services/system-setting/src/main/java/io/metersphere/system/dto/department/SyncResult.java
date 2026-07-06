package io.metersphere.system.dto.department;

import lombok.Data;

@Data
public class SyncResult {
    private String organizationId;
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
    private Integer deptCreated;
    private Integer deptUpdated;
    private Integer deptDisabled;
    private Integer userCreated;
    private Integer userUpdated;
    private Integer userDisabled;
}
