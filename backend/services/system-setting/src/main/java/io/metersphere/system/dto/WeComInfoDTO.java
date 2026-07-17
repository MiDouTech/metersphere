package io.metersphere.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class WeComInfoDTO implements Serializable {
    @Schema(description = "企业 ID")
    private String corpId;
    @Schema(description = "应用 AgentId")
    private String agentId;
    @Schema(description = "应用 Secret")
    private String appSecret;
    @Schema(description = "回调地址")
    private String callBack;
    @Schema(description = "是否开启")
    private Boolean enable;
    @Schema(description = "是否校验通过")
    private Boolean valid;
    @Schema(description = "登录 state")
    private String state;
}
