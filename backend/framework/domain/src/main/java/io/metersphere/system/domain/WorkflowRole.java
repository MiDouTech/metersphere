package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class WorkflowRole implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String flowId;
    private String code;
    private String name;
    private String roleType;
    private String roleId;
    private String fieldKey;
    private Boolean enabled;
    private Long createTime;
    private Long updateTime;
}
