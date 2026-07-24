package io.metersphere.system.service;

import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.constants.DefaultHubConstants;
import io.metersphere.sdk.constants.InternalUserRole;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.domain.UserRoleRelationExample;
import io.metersphere.system.mapper.ExtSystemProjectMapper;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 默认项目（枢纽）保护与成员组织权限授予/回收
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class DefaultHubProjectService {

    @Resource
    private ExtSystemProjectMapper extSystemProjectMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private UserRoleRelationMapper userRoleRelationMapper;

    public String getDefaultProjectId() {
        return extSystemProjectMapper.selectDefaultProjectId();
    }

    public boolean isDefaultProject(String projectId) {
        if (StringUtils.isBlank(projectId)) {
            return false;
        }
        Boolean flag = extSystemProjectMapper.isDefaultProject(projectId);
        return BooleanUtils.isTrue(flag);
    }

    public void assertNotDefaultProject(String projectId) {
        if (isDefaultProject(projectId)) {
            throw new MSException(Translator.get("default_project_not_allow_delete"));
        }
    }

    public void assertOrganizationNotChangedForDefault(Project existing, String newOrganizationId) {
        if (existing == null || !isDefaultProject(existing.getId())) {
            return;
        }
        if (StringUtils.isNotBlank(newOrganizationId)
                && !StringUtils.equals(existing.getOrganizationId(), newOrganizationId)) {
            throw new MSException(Translator.get("default_project_organization_not_allow_change"));
        }
    }

    /**
     * 加入默认项目后：绑定枢纽项目角色 + 条件补授组织设置角色
     */
    public void onMembersJoinedDefaultProject(String projectId, List<String> userIds, String operator) {
        if (!isDefaultProject(projectId) || CollectionUtils.isEmpty(userIds)) {
            return;
        }
        Project project = projectMapper.selectByPrimaryKey(projectId);
        if (project == null) {
            return;
        }
        String orgId = project.getOrganizationId();
        for (String userId : userIds) {
            ensureProjectHubRole(projectId, userId, orgId, operator);
            ensureOrgSettingRole(orgId, userId, operator);
        }
    }

    /**
     * 离开默认项目：回收本机制授予的组织设置角色（不误删 org_admin）
     */
    public void onMembersLeftDefaultProject(String projectId, List<String> userIds) {
        if (!isDefaultProject(projectId) || CollectionUtils.isEmpty(userIds)) {
            return;
        }
        Project project = projectMapper.selectByPrimaryKey(projectId);
        if (project == null) {
            return;
        }
        String orgId = project.getOrganizationId();
        for (String userId : userIds) {
            UserRoleRelationExample example = new UserRoleRelationExample();
            example.createCriteria()
                    .andUserIdEqualTo(userId)
                    .andSourceIdEqualTo(orgId)
                    .andRoleIdEqualTo(DefaultHubConstants.ROLE_HUB_ORG_SETTING);
            userRoleRelationMapper.deleteByExample(example);
        }
    }

    private void ensureProjectHubRole(String projectId, String userId, String orgId, String operator) {
        UserRoleRelationExample example = new UserRoleRelationExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andSourceIdEqualTo(projectId)
                .andRoleIdEqualTo(DefaultHubConstants.ROLE_HUB_PROJECT_MEMBER);
        if (userRoleRelationMapper.countByExample(example) > 0) {
            return;
        }
        UserRoleRelation relation = new UserRoleRelation();
        relation.setId(IDGenerator.nextStr());
        relation.setUserId(userId);
        relation.setRoleId(DefaultHubConstants.ROLE_HUB_PROJECT_MEMBER);
        relation.setSourceId(projectId);
        relation.setOrganizationId(orgId);
        relation.setCreateTime(System.currentTimeMillis());
        relation.setCreateUser(operator);
        userRoleRelationMapper.insert(relation);
    }

    private void ensureOrgSettingRole(String orgId, String userId, String operator) {
        // 已是组织管理员：幂等跳过
        UserRoleRelationExample adminExample = new UserRoleRelationExample();
        adminExample.createCriteria()
                .andUserIdEqualTo(userId)
                .andSourceIdEqualTo(orgId)
                .andRoleIdEqualTo(InternalUserRole.ORG_ADMIN.getValue());
        if (userRoleRelationMapper.countByExample(adminExample) > 0) {
            return;
        }
        UserRoleRelationExample exist = new UserRoleRelationExample();
        exist.createCriteria()
                .andUserIdEqualTo(userId)
                .andSourceIdEqualTo(orgId)
                .andRoleIdEqualTo(DefaultHubConstants.ROLE_HUB_ORG_SETTING);
        if (userRoleRelationMapper.countByExample(exist) > 0) {
            return;
        }
        UserRoleRelation relation = new UserRoleRelation();
        relation.setId(IDGenerator.nextStr());
        relation.setUserId(userId);
        relation.setRoleId(DefaultHubConstants.ROLE_HUB_ORG_SETTING);
        relation.setSourceId(orgId);
        relation.setOrganizationId(orgId);
        relation.setCreateTime(System.currentTimeMillis());
        relation.setCreateUser(operator);
        userRoleRelationMapper.insert(relation);
    }
}
