package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentTokenPageRequest {
    private String keyword;
    private String status;
    private long current = 1;
    private long pageSize = 10;
}
