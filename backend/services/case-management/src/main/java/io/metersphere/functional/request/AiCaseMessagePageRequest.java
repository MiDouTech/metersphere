package io.metersphere.functional.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiCaseMessagePageRequest {
    @NotBlank
    private String projectId;
    @NotBlank
    private String conversationId;
    private Long beforeTime;
    private String beforeId;
    private Integer pageSize;
}
