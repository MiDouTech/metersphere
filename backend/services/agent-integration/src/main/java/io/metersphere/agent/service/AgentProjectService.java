package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentProjectAddMembersRequest;
import io.metersphere.agent.dto.AgentProjectCreateRequest;
import io.metersphere.agent.dto.AgentProjectDTO;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.constants.InternalUserRole;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.domain.Organization;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.domain.UserRoleRelationExample;
import io.metersphere.system.dto.AddProjectRequest;
import io.metersphere.system.dto.ProjectDTO;
import io.metersphere.system.dto.request.ProjectAddMemberRequest;
import io.metersphere.system.mapper.OrganizationMapper;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import io.metersphere.system.service.OrganizationProjectService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentProjectService {
    @Resource
    private OrganizationProjectService organizationProjectService;
    @Resource
    private OrganizationMapper organizationMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private UserRoleRelationMapper userRoleRelationMapper;
    @Resource
    private AgentExecLogService agentExecLogService;

    public AgentProjectDTO create(AgentProjectCreateRequest request) {
        String userId = requireUserId();
        assertOrgAccessible(request.getOrganizationId(), userId);

        AddProjectRequest add = new AddProjectRequest();
        add.setOrganizationId(request.getOrganizationId());
        add.setName(request.getName());
        add.setDescription(request.getDescription());
        add.setUserIds(request.getUserIds());
        // 未指定模块时默认开启用例/缺陷/计划/接口，避免空 moduleSetting 影响后续写闭环
        List<String> moduleIds = request.getModuleIds();
        if (CollectionUtils.isEmpty(moduleIds)) {
            moduleIds = List.of("caseManagement", "bugManagement", "testPlan", "apiTest");
        }
        add.setModuleIds(moduleIds);
        add.setResourcePoolIds(request.getResourcePoolIds());
        if (request.getAllResourcePool() != null) {
            add.setAllResourcePool(request.getAllResourcePool());
        }
        add.setEnable(true);

        ProjectDTO created = organizationProjectService.add(add, userId);
        Map<String, Object> audit = new HashMap<>();
        audit.put("organizationId", request.getOrganizationId());
        audit.put("name", request.getName());
        audit.put("userIds", request.getUserIds());
        audit.put("tokenId", currentTokenId());
        agentExecLogService.audit("PROJECT_CREATE", created.getId(), JSON.toJSONString(audit));
        return toDto(created);
    }

    public void addMembers(AgentProjectAddMembersRequest request) {
        String userId = requireUserId();
        Project project = projectMapper.selectByPrimaryKey(request.getProjectId());
        if (project == null) {
            throw new MSException("项目不存在: " + request.getProjectId());
        }
        assertOrgAccessible(project.getOrganizationId(), userId);

        ProjectAddMemberRequest memberRequest = new ProjectAddMemberRequest();
        memberRequest.setProjectId(request.getProjectId());
        memberRequest.setUserIds(request.getUserIds());
        // CommonProjectService.addProjectUser 对 userRoleIds 不做空保护；未传时默认项目成员
        List<String> roleIds = request.getUserRoleIds();
        if (CollectionUtils.isEmpty(roleIds)) {
            roleIds = Collections.singletonList(InternalUserRole.PROJECT_MEMBER.getValue());
        }
        memberRequest.setUserRoleIds(roleIds);
        organizationProjectService.orgAddProjectMember(memberRequest, userId);

        Map<String, Object> audit = new HashMap<>();
        audit.put("projectId", request.getProjectId());
        audit.put("userIds", request.getUserIds());
        audit.put("tokenId", currentTokenId());
        agentExecLogService.audit("PROJECT_ADD_MEMBERS", request.getProjectId(), JSON.toJSONString(audit));
    }

    public AgentProjectDTO get(String id) {
        ProjectDTO project = organizationProjectService.get(id);
        if (project == null) {
            throw new MSException("项目不存在: " + id);
        }
        return toDto(project);
    }

    private void assertOrgAccessible(String organizationId, String userId) {
        Organization organization = organizationMapper.selectByPrimaryKey(organizationId);
        if (organization == null) {
            throw new MSException("组织不存在: " + organizationId);
        }
        UserRoleRelationExample example = new UserRoleRelationExample();
        example.createCriteria().andSourceIdEqualTo(organizationId).andUserIdEqualTo(userId);
        List<UserRoleRelation> relations = userRoleRelationMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(relations)) {
            throw new MSException("当前用户不属于组织: " + organizationId);
        }
    }

    private String requireUserId() {
        String userId = SessionUtils.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new MSException("无法解析 Agent Token 对应用户");
        }
        return userId;
    }

    private String currentTokenId() {
        AgentToken token = AgentTokenContext.get();
        return token == null ? null : token.getId();
    }

    private AgentProjectDTO toDto(ProjectDTO source) {
        AgentProjectDTO dto = new AgentProjectDTO();
        dto.setId(source.getId());
        dto.setName(source.getName());
        dto.setOrganizationId(source.getOrganizationId());
        dto.setDescription(source.getDescription());
        dto.setEnable(source.getEnable());
        return dto;
    }
}
