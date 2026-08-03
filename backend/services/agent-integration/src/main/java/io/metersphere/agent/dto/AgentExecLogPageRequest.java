package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentExecLogPageRequest {
    private String caseId;
    private String executedBy;
    /** 审计动作码，对应 last_exec_result */
    private String action;
    private String createUser;
    private long current = 1;
    private long pageSize = 10;
}
