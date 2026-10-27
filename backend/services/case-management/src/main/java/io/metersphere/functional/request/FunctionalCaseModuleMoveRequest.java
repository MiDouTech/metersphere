package io.metersphere.functional.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FunctionalCaseModuleMoveRequest {
    @NotBlank
    private String moduleId;
    @NotBlank
    private String parentId;
    @NotNull
    private Long expectedUpdateTime;
}
