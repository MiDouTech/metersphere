package io.metersphere.system.dto.permission;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class UiPermissionSetDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Set<String> visible = new LinkedHashSet<>();
    private Set<String> operable = new LinkedHashSet<>();
}
