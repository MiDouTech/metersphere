package io.metersphere.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class PlatformSourceDTO implements Serializable {
    @Schema(description = "平台标识：WE_COM / DING_TALK / LARK / LARK_SUITE")
    private String platform;
    @Schema(description = "是否开启")
    private Boolean enable;
    @Schema(description = "是否校验通过")
    private Boolean valid;
    @Schema(description = "是否已配置")
    private Boolean hasConfig;
}
