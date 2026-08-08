package io.metersphere.functional.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiCaseConversationPageRequest {
    @NotBlank
    private String projectId;
    private String status;
    private Integer current;
    private Integer pageSize;
}
