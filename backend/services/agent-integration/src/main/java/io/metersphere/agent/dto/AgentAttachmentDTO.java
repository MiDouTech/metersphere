package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AgentAttachmentDTO {
    private String id;
    private String fileId;
    private String fileName;
    private Integer stepNum;
    @Schema(description = "下载路径（相对 API 前缀）")
    private String downloadPath;
}
