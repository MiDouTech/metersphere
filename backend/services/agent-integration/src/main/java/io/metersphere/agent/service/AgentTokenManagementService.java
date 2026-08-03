package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.dto.AgentTokenCreateRequest;
import io.metersphere.agent.dto.AgentTokenCreateResponse;
import io.metersphere.agent.dto.AgentTokenListItemDTO;
import io.metersphere.agent.dto.AgentTokenPageRequest;
import io.metersphere.agent.dto.AgentTokenUpdateRequest;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.agent.security.AgentTokenScopeParser;
import io.metersphere.project.domain.Project;
import io.metersphere.project.domain.ProjectExample;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.domain.User;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.domain.UserRoleRelationExample;
import io.metersphere.system.mapper.AgentTokenMapper;
import io.metersphere.system.mapper.ExtUserMapper;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentTokenManagementService {
    private static final BCryptPasswordEncoder TOKEN_SECRET_ENCODER = new BCryptPasswordEncoder(12);

    @Resource
    private AgentTokenMapper agentTokenMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private ExtUserMapper extUserMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private UserRoleRelationMapper userRoleRelationMapper;

    public AgentTokenCreateResponse create(AgentTokenCreateRequest request) {
        String inputUser = StringUtils.isNotBlank(request.getUserId())
                ? request.getUserId()
                : SessionUtils.getUserId();
        String boundUserId = resolveBoundUserId(inputUser);
        List<String> projectIds = normalizeProjectIds(request.getProjectIds(), request.getProjectId());
        validateProjectsExist(projectIds);

        GeneratedToken generatedToken = generateRawToken(null);
        AgentToken token = new AgentToken();
        token.setId(IDGenerator.nextStr());
        applyGeneratedToken(token, generatedToken);
        token.setName(request.getName());
        token.setTokenPrefix(AgentConstants.TOKEN_PREFIX);
        token.setUserId(boundUserId);
        token.setProjectId(AgentTokenProjectAccess.primaryProjectId(projectIds));
        token.setProjectIds(AgentTokenProjectAccess.toStorageJson(projectIds));
        token.setScopes(AgentTokenScopeParser.normalizeAndValidate(request.getScopes()));
        token.setClientType(normalizeClientType(request.getClientType()));
        token.setExpireTime(request.getExpireTime());
        token.setEnable(true);
        token.setStatus("ACTIVE");
        token.setInvocationCount(0L);
        token.setTokenVersion(2);
        token.setCreateTime(System.currentTimeMillis());
        token.setCreateUser(SessionUtils.getUserId());
        agentTokenMapper.insert(token);

        AgentTokenCreateResponse response = new AgentTokenCreateResponse();
        response.setId(token.getId());
        response.setName(token.getName());
        response.setToken(generatedToken.rawToken());
        response.setScopes(token.getScopes());
        response.setExpireTime(token.getExpireTime());
        response.setWarning("Token 是 Agent API 登录凭证（明文仅展示一次）。关联用户为执行身份，不是管理员密码。");
        return response;
    }

    public AgentTokenCreateResponse createPersonal(AgentTokenCreateRequest request) {
        request.setUserId(SessionUtils.getUserId());
        validatePersonalProjectAccess(normalizeProjectIds(request.getProjectIds(), request.getProjectId()));
        return create(request);
    }

    public Pager<List<AgentTokenListItemDTO>> page(AgentTokenPageRequest request) {
        long current = Math.max(request.getCurrent(), 1);
        long pageSize = Math.max(request.getPageSize(), 1);
        long offset = (current - 1) * pageSize;
        long total = agentTokenMapper.countPage(request.getKeyword());
        List<AgentTokenListItemDTO> list = agentTokenMapper.selectPage(request.getKeyword(), offset, pageSize).stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
        return new Pager<>(list, total, pageSize, current);
    }

    public Pager<List<AgentTokenListItemDTO>> pagePersonal(AgentTokenPageRequest request) {
        long current = Math.max(request.getCurrent(), 1);
        long pageSize = Math.max(request.getPageSize(), 1);
        long offset = (current - 1) * pageSize;
        String userId = SessionUtils.getUserId();
        long total = agentTokenMapper.countUserPage(userId, request.getKeyword());
        List<AgentTokenListItemDTO> list = agentTokenMapper.selectUserPage(userId, request.getKeyword(), offset, pageSize).stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
        return new Pager<>(list, total, pageSize, current);
    }

    public void update(AgentTokenUpdateRequest request) {
        AgentToken existing = agentTokenMapper.selectByPrimaryKey(request.getId());
        if (existing == null) {
            throw new MSException("Token 不存在");
        }
        AgentToken update = new AgentToken();
        update.setId(request.getId());
        update.setName(request.getName());
        update.setScopes(AgentTokenScopeParser.normalizeAndValidate(request.getScopes()));
        update.setClientType(StringUtils.isBlank(request.getClientType()) ? null : normalizeClientType(request.getClientType()));
        update.setExpireTime(request.getExpireTime());
        update.setEnable(request.getEnable());
        update.setStatus(statusFromEnable(request.getEnable()));
        agentTokenMapper.updateByPrimaryKeySelective(update);
        if (request.getProjectIds() != null || request.getProjectId() != null) {
            List<String> projectIds = normalizeProjectIds(request.getProjectIds(), request.getProjectId());
            validateProjectsExist(projectIds);
            AgentToken projectUpdate = new AgentToken();
            projectUpdate.setId(request.getId());
            projectUpdate.setProjectId(AgentTokenProjectAccess.primaryProjectId(projectIds));
            projectUpdate.setProjectIds(AgentTokenProjectAccess.toStorageJson(projectIds));
            agentTokenMapper.updateProjectAccess(projectUpdate);
        }
    }

    public void updatePersonal(AgentTokenUpdateRequest request) {
        AgentToken existing = requirePersonalToken(request.getId());
        if (request.getProjectIds() != null || request.getProjectId() != null) {
            validatePersonalProjectAccess(normalizeProjectIds(request.getProjectIds(), request.getProjectId()));
        }
        update(request);
    }

    public void enablePersonal(String id) {
        changePersonalEnable(id, true);
    }

    public void disablePersonal(String id) {
        changePersonalEnable(id, false);
    }

    public void deletePersonal(String id) {
        requirePersonalToken(id);
        delete(id);
    }

    public void revoke(String id) {
        AgentToken existing = agentTokenMapper.selectByPrimaryKey(id);
        if (existing == null) {
            throw new MSException("Token 涓嶅瓨鍦?");
        }
        AgentToken update = new AgentToken();
        update.setId(id);
        update.setEnable(false);
        update.setStatus("REVOKED");
        update.setRevokedAt(System.currentTimeMillis());
        update.setRevokedBy(SessionUtils.getUserId());
        agentTokenMapper.updateByPrimaryKeySelective(update);
    }

    public AgentTokenListItemDTO testPersonal(String id) {
        return toListItem(requirePersonalToken(id));
    }

    public void delete(String id) {
        agentTokenMapper.deleteByPrimaryKey(id);
    }

    private GeneratedToken generateRawToken(String existingPublicId) {
        String publicId = StringUtils.defaultIfBlank(existingPublicId,
                UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        String secret = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String rawToken = AgentConstants.TOKEN_PREFIX + publicId + "_" + secret;
        return new GeneratedToken(publicId, secret, rawToken);
    }

    private void applyGeneratedToken(AgentToken token, GeneratedToken generatedToken) {
        token.setPublicId(generatedToken.publicId());
        token.setTokenHash(DigestUtils.sha256Hex(UUID.randomUUID().toString() + generatedToken.publicId()));
        token.setSecretHash(TOKEN_SECRET_ENCODER.encode(generatedToken.secret()));
        token.setDisplayPrefix(StringUtils.left(generatedToken.rawToken(), 20) + "...");
    }

    /**
     * 支持平台用户 ID 或企微 wecom_userid；最终落库为 user.id。
     */
    String resolveBoundUserId(String input) {
        String key = StringUtils.trimToEmpty(input);
        if (StringUtils.isBlank(key)) {
            throw new MSException("关联用户不能为空（用作 Agent 登录/执行身份）");
        }
        User byId = userMapper.selectByPrimaryKey(key);
        if (byId != null && !BooleanUtils.isTrue(byId.getDeleted())) {
            return byId.getId();
        }
        User byWecom = extUserMapper.selectByWecomUserid(key);
        if (byWecom != null && !BooleanUtils.isTrue(byWecom.getDeleted())) {
            return byWecom.getId();
        }
        throw new MSException("用户不存在，请填写平台用户ID或企微UserID: " + key);
    }

    private List<String> normalizeProjectIds(List<String> projectIds, String legacyProjectId) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (CollectionUtils.isNotEmpty(projectIds)) {
            for (String id : projectIds) {
                if (StringUtils.isNotBlank(id)) {
                    set.add(id.trim());
                }
            }
        } else if (StringUtils.isNotBlank(legacyProjectId)) {
            set.add(legacyProjectId.trim());
        }
        return new ArrayList<>(set);
    }

    private AgentToken requirePersonalToken(String id) {
        AgentToken existing = agentTokenMapper.selectByPrimaryKey(id);
        if (existing == null) {
            throw new MSException("Token 涓嶅瓨鍦?");
        }
        if (!StringUtils.equals(existing.getUserId(), SessionUtils.getUserId())) {
            throw new MSException("鏃犳潈鎿嶄綔浠栦汉 Agent Token");
        }
        return existing;
    }

    private void changePersonalEnable(String id, boolean enable) {
        requirePersonalToken(id);
        AgentToken update = new AgentToken();
        update.setId(id);
        update.setEnable(enable);
        update.setStatus(enable ? "ACTIVE" : "DISABLED");
        agentTokenMapper.updateByPrimaryKeySelective(update);
    }

    private String statusFromEnable(Boolean enable) {
        if (enable == null) {
            return null;
        }
        return enable ? "ACTIVE" : "DISABLED";
    }

    private String normalizeClientType(String clientType) {
        return StringUtils.defaultIfBlank(clientType, "GENERIC").trim().toUpperCase(Locale.ROOT);
    }

    private void validateProjectsExist(List<String> projectIds) {
        if (CollectionUtils.isEmpty(projectIds)) {
            return;
        }
        for (String projectId : projectIds) {
            Project project = projectMapper.selectByPrimaryKey(projectId);
            if (project == null || BooleanUtils.isTrue(project.getDeleted())) {
                throw new MSException("项目不存在: " + projectId);
            }
        }
    }

    private void validatePersonalProjectAccess(List<String> projectIds) {
        if (CollectionUtils.isEmpty(projectIds)) {
            return;
        }
        Set<String> accessibleProjectIds = accessibleProjectIds(SessionUtils.getUserId());
        List<String> denied = projectIds.stream()
                .filter(projectId -> !accessibleProjectIds.contains(projectId))
                .toList();
        if (CollectionUtils.isNotEmpty(denied)) {
            throw new MSException("Current user cannot create Agent Token for projects: " + StringUtils.join(denied, ","));
        }
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

    private AgentTokenListItemDTO toListItem(AgentToken source) {
        AgentTokenListItemDTO target = new AgentTokenListItemDTO();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setUserId(source.getUserId());
        target.setScopes(source.getScopes());
        target.setExpireTime(source.getExpireTime());
        target.setEnable(source.getEnable());
        target.setClientType(source.getClientType());
        target.setStatus(StringUtils.defaultIfBlank(source.getStatus(), BooleanUtils.isTrue(source.getEnable()) ? "ACTIVE" : "DISABLED"));
        target.setDisplayPrefix(source.getDisplayPrefix());
        target.setLastUsedAt(source.getLastUsedAt());
        target.setInvocationCount(source.getInvocationCount());
        target.setCreateTime(source.getCreateTime());
        target.setCreateUser(source.getCreateUser());
        List<String> ids = AgentTokenProjectAccess.parseProjectIds(source);
        target.setProjectIds(ids);
        target.setProjectId(AgentTokenProjectAccess.primaryProjectId(ids));
        if (CollectionUtils.isEmpty(ids)) {
            target.setProjectScopeLabel("全部项目");
        } else if (ids.size() == 1) {
            target.setProjectScopeLabel(ids.get(0));
        } else {
            target.setProjectScopeLabel(ids.size() + " 个项目");
        }
        return target;
    }

    private record GeneratedToken(String publicId, String secret, String rawToken) {
    }
}
