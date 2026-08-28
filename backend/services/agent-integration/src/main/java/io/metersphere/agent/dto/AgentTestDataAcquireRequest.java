package io.metersphere.agent.dto;
import jakarta.validation.constraints.*;import lombok.Data;
@Data public class AgentTestDataAcquireRequest {@NotBlank private String leaseId;@NotBlank private String datasetId;@NotBlank private String dataKey;@Min(1000)@Max(86400000) private long ttlMs;}
