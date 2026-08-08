package io.metersphere.functional.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiCaseConversationCreateRequest {
    @NotBlank
    private String projectId;
    @NotBlank
    private String organizationId;
    private String modelSourceId;
    @Size(max = 32)
    private String resourceType;
    @Size(max = 50)
    private String resourceId;
    @Size(max = 255)
    private String title;
}
