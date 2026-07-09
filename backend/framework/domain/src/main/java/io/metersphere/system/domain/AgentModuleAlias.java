package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class AgentModuleAlias implements Serializable {
    private String id;
    private String projectId;
    private String alias;
    private String moduleId;
    private Long createTime;
    private String createUser;
}
