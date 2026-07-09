package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AgentAttachmentUploadResponse {
    private String attachmentId;
    private String fileId;
    private String fileName;
    @Schema(description = "下载路径（相对 API 前缀）")
    private String downloadPath;
}
