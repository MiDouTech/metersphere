package io.metersphere.system.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiOAuthAuthorizeRequest {
    @NotBlank private String connectionId;
    @NotBlank private String redirectUri;
}
