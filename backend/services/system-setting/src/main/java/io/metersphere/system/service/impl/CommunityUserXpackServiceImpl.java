package io.metersphere.system.service.impl;

import io.metersphere.engine.util.CFTEncryptUtils;
import io.metersphere.sdk.constants.EmailInviteSource;
import io.metersphere.sdk.constants.UserSource;
import io.metersphere.sdk.util.CodingUtils;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.User;
import io.metersphere.system.domain.UserInvite;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.dto.request.UserRegisterRequest;
import io.metersphere.system.dto.user.UserCreateInfo;
import io.metersphere.system.dto.user.request.UserBatchCreateRequest;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import io.metersphere.system.service.UserRoleRelationService;
import io.metersphere.system.service.UserXpackService;
import io.metersphere.system.uid.IDGenerator;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Community edition UserXpackService: no quota restrictions, persists users locally.
 * Registered via {@link io.metersphere.system.config.CommunityXpackConfiguration}.
 */
public class CommunityUserXpackServiceImpl implements UserXpackService {

    private static final int SUCCESS = 0;

    private final UserMapper userMapper;
    private final UserRoleRelationMapper userRoleRelationMapper;
    private final UserRoleRelationService userRoleRelationService;

    public CommunityUserXpackServiceImpl(UserMapper userMapper,
                                         UserRoleRelationMapper userRoleRelationMapper,
                                         UserRoleRelationService userRoleRelationService) {
        this.userMapper = userMapper;
        this.userRoleRelationMapper = userRoleRelationMapper;
        this.userRoleRelationService = userRoleRelationService;
    }

    @Override
    public int GWHowToAddUser(UserBatchCreateRequest userCreateDTO, String source, String operator) {
        if (CollectionUtils.isEmpty(userCreateDTO.getUserInfoList())) {
            return SUCCESS;
        }
        long now = System.currentTimeMillis();
        for (UserCreateInfo userCreateInfo : userCreateDTO.getUserInfoList()) {
            String userId = IDGenerator.nextStr();
            userCreateInfo.setId(userId);
            User user = buildUser(userId, userCreateInfo.getName(), userCreateInfo.getEmail(),
                    userCreateInfo.getPhone(), source, operator, now, CodingUtils.md5(userCreateInfo.getEmail()));
            userMapper.insert(user);
            userRoleRelationService.updateUserSystemGlobalRole(user, operator, userCreateDTO.getUserRoleIdList());
        }
        return SUCCESS;
    }

    @Override
    public int GWHowToAddUser(UserRegisterRequest registerRequest, UserInvite userInvite) {
        String userId = IDGenerator.nextStr();
        long now = System.currentTimeMillis();
        User user = buildUser(userId, registerRequest.getName(), userInvite.getEmail(),
                registerRequest.getPhone(), UserSource.LOCAL.name(), userInvite.getInviteUser(), now,
                CodingUtils.md5(registerRequest.getPassword()));
        userMapper.insert(user);
        assignInviteRoles(user, userInvite, userInvite.getInviteUser(), now);
        return SUCCESS;
    }

    @Override
    public int GWHowToChangeUser(List<String> userIds, boolean enable, String operator) {
        if (CollectionUtils.isEmpty(userIds)) {
            return SUCCESS;
        }
        long now = System.currentTimeMillis();
        for (String userId : userIds) {
            User user = new User();
            user.setId(userId);
            user.setEnable(enable);
            user.setUpdateUser(operator);
            user.setUpdateTime(now);
            userMapper.updateByPrimaryKeySelective(user);
        }
        return SUCCESS;
    }

    @Override
    public int GWHowToDeleteUser(List<String> userIdList, String operator) {
        if (CollectionUtils.isEmpty(userIdList)) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int successCount = 0;
        for (String userId : userIdList) {
            User user = new User();
            user.setId(userId);
            user.setDeleted(true);
            user.setUpdateUser(operator);
            user.setUpdateTime(now);
            userMapper.updateByPrimaryKeySelective(user);
            successCount++;
        }
        return successCount;
    }

    private User buildUser(String userId, String name, String email, String phone, String source,
                           String operator, long now, String password) {
        User user = new User();
        user.setId(userId);
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setSource(source);
        user.setPassword(password);
        user.setEnable(true);
        user.setDeleted(false);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setCreateUser(operator);
        user.setUpdateUser(operator);
        user.setCftToken(CFTEncryptUtils.aesEncrypt(userId));
        return user;
    }

    private void assignInviteRoles(User user, UserInvite userInvite, String operator, long createTime) {
        List<String> roleIds = JSON.parseArray(StringUtils.defaultIfBlank(userInvite.getRoles(), "[]"), String.class);
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        if (isProjectInvite(userInvite)) {
            batchInsertRoleRelations(user.getId(), roleIds, userInvite.getProjectId(), userInvite.getOrganizationId(), operator, createTime);
            return;
        }
        if (isOrganizationInvite(userInvite)) {
            batchInsertRoleRelations(user.getId(), roleIds, userInvite.getOrganizationId(), userInvite.getOrganizationId(), operator, createTime);
            return;
        }
        userRoleRelationService.updateUserSystemGlobalRole(user, operator, roleIds);
    }

    private boolean isProjectInvite(UserInvite userInvite) {
        return StringUtils.isNotBlank(userInvite.getProjectId())
                && !StringUtils.equals(userInvite.getProjectId(), EmailInviteSource.SYSTEM.name());
    }

    private boolean isOrganizationInvite(UserInvite userInvite) {
        return StringUtils.isNotBlank(userInvite.getOrganizationId())
                && !StringUtils.equals(userInvite.getOrganizationId(), EmailInviteSource.SYSTEM.name());
    }

    private void batchInsertRoleRelations(String userId, List<String> roleIds, String sourceId,
                                          String organizationId, String operator, long createTime) {
        List<UserRoleRelation> relations = new ArrayList<>();
        for (String roleId : roleIds) {
            UserRoleRelation relation = new UserRoleRelation();
            relation.setId(IDGenerator.nextStr());
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setSourceId(sourceId);
            relation.setOrganizationId(organizationId);
            relation.setCreateTime(createTime);
            relation.setCreateUser(operator);
            relations.add(relation);
        }
        userRoleRelationMapper.batchInsert(relations);
    }
}
