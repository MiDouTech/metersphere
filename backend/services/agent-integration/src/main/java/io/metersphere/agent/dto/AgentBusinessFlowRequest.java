package io.metersphere.agent.dto;
import jakarta.validation.constraints.*;import lombok.Data;import java.util.*;
@Data public class AgentBusinessFlowRequest {@NotBlank private String projectId;@NotBlank@Size(max=255)private String name;@NotEmpty@Size(max=200)private List<Map<String,Object>> nodes;@NotNull@Size(max=500)private List<Map<String,Object>> edges;@NotBlank private String entryNodeId;@NotEmpty@Size(max=50)private List<Map<String,Object>> exitConditions;@NotEmpty@Size(max=32)private List<String> allowedActions;private String status;private Integer version;}
