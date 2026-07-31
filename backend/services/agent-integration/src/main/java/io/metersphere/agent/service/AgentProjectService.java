package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentProjectAddMembersRequest;
import io.metersphere.agent.dto.AgentProjectCreateRequest;
import io.metersphere.agent.dto.AgentProjectDTO;
import io.metersphere.agent.dto.AgentProjectSearchRequest;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.project.domain.Project;
import io.metersphere.project.domain.ProjectExample;
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
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            throw new MSException("Project does not exist: " + request.getProjectId());
        }
        assertOrgAccessible(project.getOrganizationId(), userId);

        ProjectAddMemberRequest memberRequest = new ProjectAddMemberRequest();
        memberRequest.setProjectId(request.getProjectId());
        memberRequest.setUserIds(request.getUserIds());
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
        String projectId = resolveProjectId(id);
        ProjectDTO project = organizationProjectService.get(projectId);
        if (project == null) {
            throw new MSException("Project does not exist: " + id);
        }
        return toDto(project);
    }

    public String resolveProjectId(String projectIdentity) {
        String identity = StringUtils.trimToEmpty(projectIdentity);
        if (StringUtils.isBlank(identity)) {
            String currentProjectId = SessionUtils.getCurrentProjectId();
            if (StringUtils.isBlank(currentProjectId)) {
                throw new MSException("Missing project identity. Use projectId with internal project id, UI project number, or project name.");
            }
            identity = currentProjectId;
        }

        String searchIdentity = identity;
        AgentProjectSearchRequest request = new AgentProjectSearchRequest();
        request.setKeyword(searchIdentity);
        request.setLimit(200);
        List<AgentProjectDTO> matched = search(request).stream()
                .filter(project -> exactProjectMatch(project, searchIdentity))
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            throw new MSException("Project not found or not accessible by current Agent Token: " + identity);
        }
        if (matched.size() > 1) {
            String candidates = matched.stream()
                    .map(project -> project.getName() + "(" + project.getNum() + ", " + project.getId() + ")")
                    .collect(Collectors.joining(", "));
            throw new MSException("Project identity matched multiple projects. Use internal project id. Candidates: " + candidates);
        }
        return matched.get(0).getId();
    }

    public List<AgentProjectDTO> search(AgentProjectSearchRequest request) {
        String keyword = StringUtils.trimToEmpty(request == null ? null : request.getKeyword());
        int limit = normalizeLimit(request == null ? null : request.getLimit());
        Set<String> candidateIds = accessibleProjectIds(requireUserId());

        AgentToken token = AgentTokenContext.get();
        List<String> tokenProjectIds = AgentTokenProjectAccess.parseProjectIds(token);
        if (CollectionUtils.isNotEmpty(tokenProjectIds)) {
            candidateIds.retainAll(new LinkedHashSet<>(tokenProjectIds));
        }
        if (candidateIds.isEmpty()) {
            return Collections.emptyList();
        }

        ProjectExample example = new ProjectExample();
        example.createCriteria().andIdIn(new ArrayList<>(candidateIds)).andDeletedEqualTo(false);
        List<Project> projects = projectMapper.selectByExample(example);

        return projects.stream()
                .filter(project -> matches(project, keyword))
                .sorted(projectComparator(keyword))
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private void assertOrgAccessible(String organizationId, String userId) {
        Organization organization = organizationMapper.selectByPrimaryKey(organizationId);
        if (organization == null) {
            throw new MSException("Organization does not exist: " + organizationId);
        }
        UserRoleRelationExample example = new UserRoleRelationExample();
        example.createCriteria().andSourceIdEqualTo(organizationId).andUserIdEqualTo(userId);
        List<UserRoleRelation> relations = userRoleRelationMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(relations)) {
            throw new MSException("Current user does not belong to organization: " + organizationId);
        }
    }

    private String requireUserId() {
        String userId = SessionUtils.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new MSException("Cannot resolve user bound to Agent Token");
        }
        return userId;
    }

    private String currentTokenId() {
        AgentToken token = AgentTokenContext.get();
        return token == null ? null : token.getId();
    }

    private Set<String> accessibleProjectIds(String userId) {
        UserRoleRelationExample relationExample = new UserRoleRelationExample();
        relationExample.createCriteria().andUserIdEqualTo(userId);
        List<UserRoleRelation> relations = userRoleRelationMapper.selectByExample(relationExample);
        if (CollectionUtils.isEmpty(relations)) {
            return new LinkedHashSet<>();
        }
        List<String> sourceIds = relations.stream()
                .map(UserRoleRelation::getSourceId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(sourceIds)) {
            return new LinkedHashSet<>();
        }

        Map<String, Project> merged = new LinkedHashMap<>();
        ProjectExample projectById = new ProjectExample();
        projectById.createCriteria().andIdIn(sourceIds).andDeletedEqualTo(false);
        projectMapper.selectByExample(projectById).forEach(project -> merged.put(project.getId(), project));

        ProjectExample projectByOrg = new ProjectExample();
        projectByOrg.createCriteria().andOrganizationIdIn(sourceIds).andDeletedEqualTo(false);
        projectMapper.selectByExample(projectByOrg).forEach(project -> merged.put(project.getId(), project));
        return new LinkedHashSet<>(merged.keySet());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }

    private boolean exactProjectMatch(AgentProjectDTO project, String identity) {
        String key = StringUtils.trimToEmpty(identity);
        String num = project.getNum() == null ? "" : String.valueOf(project.getNum());
        return StringUtils.equals(project.getId(), key)
                || StringUtils.equals(num, key)
                || StringUtils.equalsIgnoreCase(project.getName(), key);
    }

    private boolean matches(Project project, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return true;
        }
        String key = StringUtils.lowerCase(keyword);
        String projectNum = project.getNum() == null ? "" : String.valueOf(project.getNum());
        return StringUtils.contains(StringUtils.lowerCase(project.getId()), key)
                || StringUtils.contains(StringUtils.lowerCase(project.getName()), key)
                || StringUtils.contains(projectNum, keyword);
    }

    private Comparator<Project> projectComparator(String keyword) {
        String key = StringUtils.lowerCase(StringUtils.trimToEmpty(keyword));
        return Comparator
                .comparingInt((Project project) -> matchRank(project, key))
                .thenComparing((Project project) -> project.getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Project::getId, Comparator.nullsLast(String::compareTo));
    }

    private int matchRank(Project project, String key) {
        if (StringUtils.isBlank(key)) {
            return 10;
        }
        String id = StringUtils.lowerCase(project.getId());
        String name = StringUtils.lowerCase(project.getName());
        String num = project.getNum() == null ? "" : String.valueOf(project.getNum());
        if (StringUtils.equals(num, key)) {
            return 0;
        }
        if (StringUtils.equals(id, key)) {
            return 1;
        }
        if (StringUtils.equals(name, key)) {
            return 2;
        }
        if (StringUtils.contains(num, key)) {
            return 3;
        }
        if (StringUtils.contains(id, key)) {
            return 4;
        }
        if (StringUtils.contains(name, key)) {
            return 5;
        }
        return 10;
    }

    private AgentProjectDTO toDto(ProjectDTO source) {
        AgentProjectDTO dto = new AgentProjectDTO();
        dto.setId(source.getId());
        dto.setNum(source.getNum());
        dto.setName(source.getName());
        dto.setOrganizationId(source.getOrganizationId());
        dto.setOrganizationName(source.getOrganizationName());
        dto.setDescription(source.getDescription());
        dto.setEnable(source.getEnable());
        return dto;
    }

    private AgentProjectDTO toDto(Project source) {
        AgentProjectDTO dto = new AgentProjectDTO();
        dto.setId(source.getId());
        dto.setNum(source.getNum());
        dto.setName(source.getName());
        dto.setOrganizationId(source.getOrganizationId());
        Organization organization = organizationMapper.selectByPrimaryKey(source.getOrganizationId());
        dto.setOrganizationName(organization == null ? null : organization.getName());
        dto.setDescription(source.getDescription());
        dto.setEnable(BooleanUtils.isTrue(source.getEnable()));
        return dto;
    }
}
