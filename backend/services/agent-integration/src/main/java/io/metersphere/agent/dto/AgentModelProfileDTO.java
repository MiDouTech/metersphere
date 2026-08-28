package io.metersphere.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentModelProfileDTO {
    private String id;
    private String organizationId;
    private String projectId;
    private String name;
    private String gatewayAppCaller;
    private String logicalModelPublicId;
    private String promptPolicyId;
    private String gatewayPromptPolicyId;
    private List<String> requiredCapabilities;
    private Integer requestTimeoutMs;
    private Integer maxOutputTokens;
    private BigDecimal maxCostAmount;
    private String currency;
    private Boolean enabled;
    private Integer version;
    private Long lastVerifiedAt;
    private String lastVerifyStatus;
    private String lastVerifyMessage;
    private Long createTime;
    private Long updateTime;
}
