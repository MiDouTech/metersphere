package io.metersphere.system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class UpdateAuthStatusRequest implements Serializable {
    @Schema(description = "认证源 ID")
    private String id;
    @Schema(description = "是否启用")
    private Boolean enable;
}
