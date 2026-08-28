package io.metersphere.agent.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data @AllArgsConstructor public class AgentExecutionCheckpointDTO { private String id; private String taskId; private String executionId; private Integer version; private String status; private String reason; private String resumeToken; private Long createdAt; }
