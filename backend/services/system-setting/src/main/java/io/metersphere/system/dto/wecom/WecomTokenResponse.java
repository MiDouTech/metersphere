package io.metersphere.system.dto.wecom;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WecomTokenResponse {
    private Integer errcode;
    private String errmsg;
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("expires_in")
    private Integer expiresIn;
}
