package io.metersphere.system.dto.permission.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RoleMemberUpdateRequest {
    @NotBlank
    private String roleId;
    @NotEmpty
    @Valid
    private List<String> userIds;
}
