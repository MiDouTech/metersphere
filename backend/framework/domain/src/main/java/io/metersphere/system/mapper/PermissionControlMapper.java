package io.metersphere.system.mapper;

import io.metersphere.system.domain.RoleAssignmentRule;
import io.metersphere.system.domain.StatusFlowRolePermission;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.domain.WorkflowDefinition;
import io.metersphere.system.domain.WorkflowRole;
import io.metersphere.system.domain.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PermissionControlMapper {
    int insertRoleAssignmentRule(RoleAssignmentRule rule);

    List<RoleAssignmentRule> selectRoleAssignmentRules(@Param("roleId") String roleId);

    List<User> selectUsersByPosition(@Param("organizationId") String organizationId,
                                     @Param("departmentId") String departmentId,
                                     @Param("positionId") String positionId);

    List<UserRoleRelation> selectExistingRoleRelations(@Param("roleId") String roleId,
                                                       @Param("userIds") List<String> userIds);

    int insertWorkflowDefinition(WorkflowDefinition workflowDefinition);

    int updateWorkflowDefinition(WorkflowDefinition workflowDefinition);

    List<WorkflowDefinition> selectWorkflowDefinitions(@Param("scene") String scene,
                                                       @Param("scopeType") String scopeType,
                                                       @Param("scopeId") String scopeId);

    WorkflowDefinition selectWorkflowDefinitionById(@Param("id") String id);

    int deleteWorkflowDefinition(@Param("id") String id);

    int insertWorkflowRole(WorkflowRole workflowRole);

    int updateWorkflowRole(WorkflowRole workflowRole);

    int deleteWorkflowRole(@Param("id") String id);

    List<WorkflowRole> selectWorkflowRoles(@Param("flowId") String flowId);

    WorkflowRole selectWorkflowRoleById(@Param("id") String id);

    int insertStatusFlowRolePermission(StatusFlowRolePermission permission);

    int deleteStatusFlowRolePermissionsByFlowId(@Param("flowId") String flowId);

    List<StatusFlowRolePermission> selectStatusFlowRolePermissions(@Param("flowId") String flowId);

    List<StatusFlowRolePermission> selectOperableTransitionPermissions(@Param("scene") String scene,
                                                                       @Param("scopeType") String scopeType,
                                                                       @Param("scopeId") String scopeId,
                                                                       @Param("fromStatusId") String fromStatusId,
                                                                       @Param("toStatusId") String toStatusId);
}
