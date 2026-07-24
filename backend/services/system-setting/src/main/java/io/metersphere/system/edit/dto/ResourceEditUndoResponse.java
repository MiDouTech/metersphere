package io.metersphere.system.edit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ResourceEditUndoResponse {
    @Schema(description = "是否成功")
    private boolean success;
    @Schema(description = "回写后的 payload（便于前端刷新）")
    private String payload;
    private int undoAvailable;
    private int redoAvailable;
    private String message;
}
