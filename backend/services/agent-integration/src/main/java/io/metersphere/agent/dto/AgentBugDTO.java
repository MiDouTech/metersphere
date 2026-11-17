package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AgentBugDTO {
    private String id;
    private Integer num;
    private String title;
    private String projectId;
    private String status;
    private String statusName;
    private String handleUser;
    private String handleUserName;
    private String createUser;
    private String createUserName;
    private Long createTime;
    private Long updateTime;
    private String description;
    private List<Map<String, Object>> images;
    private Boolean imagesComplete;
    private List<Map<String, Object>> attachmentImages;
    private Boolean attachmentImagesComplete;
    private List<String> imageWarnings;
    private String templateId;
    private List<String> tags;
    private String caseId;
    private Integer relationCaseCount;
    @Schema(description = "自定义字段 fieldId -> value")
    private Map<String, String> customFields;
}
