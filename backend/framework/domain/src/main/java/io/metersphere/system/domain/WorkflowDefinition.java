package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class WorkflowDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String code;
    private String name;
    private String scene;
    private String scopeType;
    private String scopeId;
    private Boolean defaultFlow;
    private Boolean activeForNew;
    private Boolean enabled;
    private String description;
    private Integer version;
    private String lifecycle;
    private Long publishedTime;
    private String publishedBy;
    private String sourceFlowId;
    private Long createTime;
    private Long updateTime;

    /**
     * 新建流程时使用：从已有流程复制角色和授权配置，不落库。
     */
    private String copyFromFlowId;
}
