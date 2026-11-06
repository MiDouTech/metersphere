package io.metersphere.agent.service;

import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.constants.InternalUserRole;
import io.metersphere.system.domain.*;
import io.metersphere.system.dto.user.*;
import io.metersphere.system.service.UserLoginService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentBugImagePermissionTests {
    UserLoginService users=mock(UserLoginService.class);ProjectMapper projects=mock(ProjectMapper.class);
    AgentBugImagePermission permissions=new AgentBugImagePermission(users,projects);
    UserDTO user=new UserDTO();
    void fixture(String roleId,String type,String source,boolean grant) {
        var project=new Project();project.setId("p");project.setDeleted(false);when(projects.selectByPrimaryKey("p")).thenReturn(project);
        user.setId("u");user.setEnable(true);user.setDeleted(false);
        var role=new UserRole();role.setId(roleId);role.setType(type);role.setEnabled(true);
        var relation=new UserRoleRelation();relation.setId("assignment");relation.setRoleId(roleId);relation.setSourceId(source);relation.setUserId("u");
        var permission=new UserRolePermission();permission.setRoleId(roleId);permission.setPermissionId(PermissionConstants.PROJECT_BUG_READ);
        var resource=new UserRoleResourceDTO();resource.setUserRole(role);resource.setUserRolePermissions(grant?List.of(permission):List.of());
        user.setUserRoles(List.of(role));user.setUserRoleRelations(List.of(relation));user.setUserRolePermissions(List.of(resource));
        when(users.getUserDTO("u")).thenReturn(user);
    }
    @Test void acceptsProjectGrantAndImmediatelyRejectsItsRemoval() {
        fixture("reader","PROJECT","p",true);assertDoesNotThrow(()->permissions.require("u","p"));
        user.setUserRoleRelations(List.of());assertThrows(io.metersphere.sdk.exception.MSException.class,()->permissions.require("u","p"));
    }
    @Test void retainsExistingSystemPermissionInheritance() {
        fixture("custom-system","SYSTEM","system",true);assertDoesNotThrow(()->permissions.require("u","p"));
        user.getUserRoles().getFirst().setEnabled(false);assertThrows(io.metersphere.sdk.exception.MSException.class,()->permissions.require("u","p"));
    }
    @Test void retainsExistingProjectAdministratorInheritance() {
        fixture(InternalUserRole.PROJECT_ADMIN.getValue(),"PROJECT","p",false);assertDoesNotThrow(()->permissions.require("u","p"));
    }
    @Test void deniesDisabledDeletedAndCrossProjectIdentities() {
        fixture("reader","PROJECT","p",true);user.setEnable(false);assertThrows(io.metersphere.sdk.exception.MSException.class,()->permissions.require("u","p"));
        user.setEnable(true);user.setDeleted(true);assertThrows(io.metersphere.sdk.exception.MSException.class,()->permissions.require("u","p"));
        user.setDeleted(false);assertThrows(io.metersphere.sdk.exception.MSException.class,()->permissions.require("u","other"));
    }
}
