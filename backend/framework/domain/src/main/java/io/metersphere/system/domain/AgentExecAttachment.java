package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class AgentExecAttachment implements Serializable {
    private String id;
    private String execHistoryId;
    private String execLogId;
    private String fileId;
    private String fileName;
    private Integer stepNum;
    private Long createTime;
    private String createUser;
}
