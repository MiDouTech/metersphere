package io.metersphere.agent.dto;
import jakarta.validation.constraints.*;import lombok.Data;
@Data public class AgentTestDataLeaseActionRequest {@NotBlank private String leaseToken;@Min(1000)@Max(86400000) private Long ttlMs;}
