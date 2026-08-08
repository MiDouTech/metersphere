package io.metersphere.functional.dto;

import lombok.Data;

@Data
public class AiCaseConversationDTO {
    private String id;
    private String projectId;
    private String organizationId;
    private String userId;
    private String title;
    private String modelSourceId;
    private String status;
    private String systemPromptVersion;
    private Long lastMessageTime;
    private Long createTime;
    private Long updateTime;
}
