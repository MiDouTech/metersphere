package io.metersphere.agent.dto;
import lombok.Data;
@Data public class AgentTestDataLeaseDTO {private String id;private String taskId;private String executionId;private String projectId;private String datasetId;private String dataKey;private String namespace;private String status;private String leaseToken;private Long expiresAt;private Long releasedAt;private Integer version;}
