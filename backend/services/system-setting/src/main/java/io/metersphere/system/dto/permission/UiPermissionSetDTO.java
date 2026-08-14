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
    /** 已纳入 UI 资源治理的按钮关联接口权限。 */
    private Set<String> managedButtonPermissions = new LinkedHashSet<>();
    private Set<String> visibleButtonPermissions = new LinkedHashSet<>();
    private Set<String> operableButtonPermissions = new LinkedHashSet<>();
    /** 已纳入 UI 权限治理的前端路由名称。 */
    private Set<String> managedRoutes = new LinkedHashSet<>();
    /** 当前角色在对应作用域内可访问的前端路由名称。 */
    private Set<String> visibleRoutes = new LinkedHashSet<>();
}
