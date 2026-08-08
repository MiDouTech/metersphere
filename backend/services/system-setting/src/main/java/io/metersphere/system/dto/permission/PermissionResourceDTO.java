package io.metersphere.system.dto.permission;

import io.metersphere.system.domain.PermissionResource;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PermissionResourceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String code;
    private String name;
    private String type;
    private String scopeType;
    private String parentCode;
    private String routeName;
    private String permissionId;
    private Boolean visibleDefault;
    private Boolean operableDefault;
    private Integer sort;
    private Boolean enabled;
    private String description;
    private List<PermissionResourceDTO> children = new ArrayList<>();

    public static PermissionResourceDTO of(PermissionResource resource) {
        PermissionResourceDTO dto = new PermissionResourceDTO();
        BeanUtils.copyProperties(resource, dto);
        return dto;
    }
}
