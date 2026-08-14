package io.metersphere.system.dto.permission.control;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class UnknownPermissionDiagnosticRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Size(max = 64)
    private String kind;

    @Size(max = 255)
    private String code;

    @Size(max = 500)
    private String context;
}
