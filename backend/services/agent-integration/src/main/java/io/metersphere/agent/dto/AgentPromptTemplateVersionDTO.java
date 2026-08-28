package io.metersphere.agent.dto;
import lombok.Data;
@Data public class AgentPromptTemplateVersionDTO { private String id;private String promptTemplateId;private String organizationId;private String name;private Integer versionNo;private String systemTemplate;private String businessTemplate;private String variableSchema;private String outputSchemaVersion;private String contentHash;private String status;private String publishedBy;private Long publishedAt;private String createUser;private Long createTime; }
