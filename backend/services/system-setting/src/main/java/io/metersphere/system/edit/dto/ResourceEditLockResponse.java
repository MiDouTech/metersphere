package io.metersphere.system.edit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ResourceEditLockResponse {
    @Schema(description = "是否获得锁")
    private boolean acquired;

    @Schema(description = "只读（未获得锁）")
    private boolean readOnly;

    @Schema(description = "持锁人ID")
    private String holderUserId;

    @Schema(description = "持锁人姓名")
    private String holderUserName;

    @Schema(description = "锁过期时间")
    private Long expireTime;

    @Schema(description = "提示文案")
    private String message;
}
