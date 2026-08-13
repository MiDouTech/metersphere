package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class StatusFlowRolePermission implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String flowId;
    private String statusFlowId;
    private String workflowRoleId;
    private Boolean visible;
    private Boolean operable;
    private Boolean enabled;
    private Long createTime;
    private Long updateTime;
}
