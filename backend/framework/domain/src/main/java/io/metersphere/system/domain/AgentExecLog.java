package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class AgentExecLog implements Serializable {
    private String id;
    private String caseId;
    private String testPlanId;
    private String testPlanCaseId;
    private String lastExecResult;
    private String executedBy;
    private String stepsSnapshot;
    private String content;
    private Long createTime;
    private String createUser;
}
