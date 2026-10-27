package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class AgentIdempotencyRecord implements Serializable {
    private String id;
    private String tokenId;
    private String toolName;
    private String requestId;
    private String requestHash;
    private String hashVersion;
    private String status;
    private String responseJson;
    private Long createTime;
}
