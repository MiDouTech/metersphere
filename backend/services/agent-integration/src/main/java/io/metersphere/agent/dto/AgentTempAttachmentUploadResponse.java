package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AgentTempAttachmentUploadResponse {
    private String attachmentId;
    private String fileId;
    private String fileName;
    private String contentType;
    private Long size;
    private String purpose;
    private Long expiresAt;
    @Schema(description = "下载路径（相对 API 前缀）")
    private String downloadPath;
}
