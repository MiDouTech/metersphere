package io.metersphere.agent.dto;
import lombok.Data;import java.util.*;
@Data public class AgentBusinessFlowDTO {private String id;private String organizationId;private String projectId;private String name;private List<Map<String,Object>> nodes;private List<Map<String,Object>> edges;private String entryNodeId;private List<Map<String,Object>> exitConditions;private List<String> allowedActions;private String status;private Integer version;private Long createTime;private Long updateTime;private String assetVersionId;}
