package io.metersphere.system.dto.permission.control;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkflowValidationDTO {
    private boolean valid;
    private List<String> errors = new ArrayList<>();
}
