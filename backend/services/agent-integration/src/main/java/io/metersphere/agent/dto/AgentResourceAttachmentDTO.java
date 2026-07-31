package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentResourceAttachmentDTO {
    private String id;
    private String associationId;
    private String fileId;
    private String fileName;
    private Long size;
    private String createUser;
    private String createUserName;
    private Long createTime;
    private Boolean local;
    private String source;
}
