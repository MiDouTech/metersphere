package io.metersphere.system.dto.permission.control;

import io.metersphere.sdk.constants.UserRoleType;
import io.metersphere.sdk.valid.EnumValue;
import io.metersphere.system.dto.sdk.request.PermissionSettingUpdateRequest;
import io.metersphere.validation.groups.Created;
import io.metersphere.validation.groups.Updated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RoleSaveRequest {
    @Size(min = 1, max = 50, groups = {Created.class, Updated.class})
    private String id;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotBlank
    @EnumValue(enumClass = UserRoleType.class)
    private String type;

    private Boolean enabled = true;

    @NotNull
    @Valid
    private List<PermissionSettingUpdateRequest.PermissionUpdateRequest> permissions;

}
