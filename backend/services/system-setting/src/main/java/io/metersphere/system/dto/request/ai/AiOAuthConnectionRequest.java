package io.metersphere.system.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiOAuthConnectionRequest {
    private String id;
    @NotBlank private String providerId;
    private String organizationId;
    private String projectId;
    @NotBlank private String authorizationUri;
    @NotBlank private String tokenUri;
    private String revokeUri;
    @NotBlank private String clientId;
    @NotBlank private String clientSecret;
    private String scopes;
}
