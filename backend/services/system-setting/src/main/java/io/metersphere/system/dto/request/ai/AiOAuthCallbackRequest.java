package io.metersphere.system.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiOAuthCallbackRequest {
    @NotBlank private String state;
    @NotBlank private String code;
    @NotBlank private String redirectUri;
}
