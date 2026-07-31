package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class AgentTempAttachment implements Serializable {
    private String id;
    private String tokenId;
    private String userId;
    private String projectId;
    private String fileId;
    private String fileName;
    private String contentType;
    private Long size;
    private String purpose;
    private Integer stepNum;
    private Boolean linked;
    private Long expiresAt;
    private Long createTime;
}
