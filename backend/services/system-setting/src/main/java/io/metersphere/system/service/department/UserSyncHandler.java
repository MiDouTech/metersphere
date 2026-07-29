package io.metersphere.system.service.department;

import io.metersphere.sdk.constants.InternalUserRole;
import io.metersphere.sdk.constants.UserSource;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.domain.Department;
import io.metersphere.system.domain.User;
import io.metersphere.system.dto.request.OrganizationMemberRequest;
import io.metersphere.system.dto.user.UserCreateInfo;
import io.metersphere.system.dto.user.request.UserBatchCreateRequest;
import io.metersphere.system.dto.wecom.WecomUserDTO;
import io.metersphere.system.mapper.ExtDepartmentMapper;
import io.metersphere.system.mapper.ExtUserMapper;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.service.OrganizationService;
import io.metersphere.system.service.SimpleUserService;
import io.metersphere.system.service.wecom.WecomContactClient;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserSyncHandler {

    @Resource
    private WecomContactClient wecomContactClient;
    @Resource
    private ExtDepartmentMapper extDepartmentMapper;
    @Resource
    private ExtUserMapper extUserMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private SimpleUserService simpleUserService;
    @Resource
    private OrganizationService organizationService;
    @Resource
    private OrgSyncEmailConflictService orgSyncEmailConflictService;

    public SyncPartResult sync(String organizationId, String operatorId, String corpId, String contactSecret) {
        SyncPartResult result = new SyncPartResult();
        long syncTime = System.currentTimeMillis();

        Map<Long, Department> wecomDeptMap = extDepartmentMapper.listByOrganizationId(organizationId).stream()
                .filter(item -> item.getWecomDeptId() != null)
                .collect(Collectors.toMap(Department::getWecomDeptId, Function.identity(), (left, right) -> left));

        List<WecomUserDTO> wecomUsers;
        try {
            wecomUsers = wecomContactClient.executeWithToken(corpId, contactSecret,
                    token -> wecomContactClient.listDepartmentUsers(token, OrgSyncConstants.ROOT_WECOM_DEPARTMENT_ID, true));
        } catch (Exception ex) {
            result.setFailed(result.getFailed() + 1);
            result.appendError("获取企微成员列表失败: " + ex.getMessage() + "; ");
            return result;
        }
        if (wecomUsers == null) {
            wecomUsers = List.of();
        }
        logFieldSample(wecomUsers);

        Map<String, User> existingUserMap = extUserMapper.listWecomUsersByOrganizationId(organizationId).stream()
                .filter(user -> StringUtils.isNotBlank(user.getWecomUserid()))
                .filter(user -> !isProtectedUser(user))
                .collect(Collectors.toMap(user -> user.getWecomUserid().trim(), Function.identity(), (left, right) -> left, HashMap::new));
        Set<String> incomingUserIds = new HashSet<>();

        for (WecomUserDTO wecomUser : wecomUsers) {
            if (wecomUser == null || StringUtils.isBlank(wecomUser.getUserid())) {
                continue;
            }
            String wecomUserId = wecomUser.getUserid().trim();
            if (wecomUserId.startsWith(OrgSyncConstants.SYSTEM_ACCOUNT_PREFIX)) {
                continue;
            }
            incomingUserIds.add(wecomUserId);
            collectMissingStats(wecomUser, result);
            try {
                User existing = existingUserMap.get(wecomUserId);
                if (existing == null) {
                    existing = extUserMapper.selectByWecomUserid(wecomUserId);
                }
                if (existing == null) {
                    createUser(wecomUser, organizationId, operatorId, resolveMainDepartment(wecomUser, wecomDeptMap), syncTime, result);
                    result.setCreated(result.getCreated() + 1);
                    continue;
                }
                if (updateUser(existing, wecomUser, organizationId, operatorId,
                        resolveMainDepartment(wecomUser, wecomDeptMap), syncTime, result)) {
                    userMapper.updateByPrimaryKeySelective(existing);
                    result.setUpdated(result.getUpdated() + 1);
                }
                ensureOrganizationMember(existing.getId(), organizationId, operatorId);
                existingUserMap.put(wecomUserId, existing);
                if (OrgSyncConstants.isPlaceholderEmail(existing.getEmail()) && resolveSyncEmail(wecomUser) == null) {
                    result.setPlaceholderEmail(result.getPlaceholderEmail() + 1);
                }
            } catch (Exception ex) {
                result.setFailed(result.getFailed() + 1);
                result.appendError("用户[" + wecomUserId + "]同步失败: " + ex.getMessage() + "; ");
            }
        }

        List<User> staleUsers = new ArrayList<>();
        if (incomingUserIds.isEmpty() && !existingUserMap.isEmpty()) {
            result.appendError("企微成员返回空列表，已跳过用户失活收敛; ");
        } else {
            staleUsers = existingUserMap.values().stream()
                    .filter(user -> StringUtils.isNotBlank(user.getWecomUserid()))
                    .filter(user -> !incomingUserIds.contains(user.getWecomUserid().trim()))
                    .filter(user -> !isProtectedUser(user))
                    .toList();
            for (User staleUser : staleUsers) {
                try {
                    if (!Boolean.FALSE.equals(staleUser.getEnable())) {
                        staleUser.setEnable(false);
                        staleUser.setSyncTime(syncTime);
                        staleUser.setUpdateTime(syncTime);
                        staleUser.setUpdateUser(operatorId);
                        userMapper.updateByPrimaryKeySelective(staleUser);
                        result.setDisabled(result.getDisabled() + 1);
                    }
                } catch (Exception ex) {
                    result.setFailed(result.getFailed() + 1);
                    result.appendError("用户[" + staleUser.getWecomUserid() + "]失活失败: " + ex.getMessage() + "; ");
                }
            }
        }

        result.setTotal(incomingUserIds.size() + staleUsers.size());
        result.setSuccess(Math.max(result.getTotal() - result.getFailed(), 0));
        String summary = String.format("企微成员 %d 人，手机号缺失 %d 人，邮箱缺失/占位 %d 人，邮箱冲突 %d 人; ",
                incomingUserIds.size(), result.getMissingMobile(),
                result.getMissingEmail() + result.getPlaceholderEmail(), result.getEmailConflict());
        result.appendError(summary);
        if (result.getMissingMobile() > 0) {
            LogUtils.warn("企微同步手机号缺失 {} 人，请检查通讯录「获取成员手机号」权限", result.getMissingMobile());
        }
        LogUtils.info(summary);
        return result;
    }

    private void createUser(WecomUserDTO wecomUser, String organizationId, String operatorId,
                            String departmentId, long syncTime, SyncPartResult result) {
        String createEmail = resolveCreateEmail(wecomUser, organizationId, null, result);
        UserBatchCreateRequest request = new UserBatchCreateRequest();
        UserCreateInfo userCreateInfo = new UserCreateInfo();
        userCreateInfo.setName(trimToNull(wecomUser.getName()));
        userCreateInfo.setEmail(createEmail);
        userCreateInfo.setPhone(trimToNull(wecomUser.getMobile()));
        request.setUserInfoList(List.of(userCreateInfo));
        request.setUserRoleIdList(List.of(InternalUserRole.MEMBER.getValue()));
        simpleUserService.addUser(request, UserSource.LOCAL.name(), operatorId);

        String userId = userCreateInfo.getId();
        ensureOrganizationMember(userId, organizationId, operatorId);

        User update = new User();
        update.setId(userId);
        update.setPassword(StringUtils.EMPTY);
        update.setWecomUserid(wecomUser.getUserid().trim());
        update.setDepartmentId(departmentId);
        update.setPosition(trimToNull(wecomUser.getPosition()));
        update.setLastOrganizationId(organizationId);
        update.setEnable(mapEnable(wecomUser.getStatus()));
        update.setSyncStatus(1);
        update.setSyncTime(syncTime);
        update.setUpdateTime(syncTime);
        update.setUpdateUser(operatorId);
        userMapper.updateByPrimaryKeySelective(update);

        // 创建时因冲突用了占位：补 pending_user_id
        String apiEmail = resolveSyncEmail(wecomUser);
        if (apiEmail != null && OrgSyncConstants.isPlaceholderEmail(createEmail)) {
            User occupied = orgSyncEmailConflictService.findOtherByEmail(apiEmail, userId);
            if (occupied != null) {
                orgSyncEmailConflictService.registerConflict(organizationId, null, wecomUser.getUserid().trim(),
                        userId, trimToNull(wecomUser.getName()), apiEmail, occupied,
                        OrgSyncConstants.EMAIL_CONFLICT_SCENE_CREATE);
            }
        }
    }

    private boolean updateUser(User existing, WecomUserDTO wecomUser, String organizationId, String operatorId,
                               String departmentId, long syncTime, SyncPartResult result) {
        boolean changed = false;
        String latestName = trimToNull(wecomUser.getName());
        if (!Objects.equals(latestName, existing.getName())) {
            existing.setName(latestName);
            changed = true;
        }
        String latestPhone = trimToNull(wecomUser.getMobile());
        if (latestPhone != null && !Objects.equals(latestPhone, existing.getPhone())) {
            existing.setPhone(latestPhone);
            changed = true;
        }
        if (applyEmailOnUpdate(existing, wecomUser, organizationId, result)) {
            changed = true;
        }
        String latestPosition = trimToNull(wecomUser.getPosition());
        if (latestPosition != null && !Objects.equals(latestPosition, existing.getPosition())) {
            existing.setPosition(latestPosition);
            changed = true;
        }
        if (!Objects.equals(departmentId, existing.getDepartmentId())) {
            existing.setDepartmentId(departmentId);
            changed = true;
        }
        Boolean latestEnable = mapEnable(wecomUser.getStatus());
        if (!Objects.equals(latestEnable, existing.getEnable())) {
            existing.setEnable(latestEnable);
            changed = true;
        }
        if (!Integer.valueOf(1).equals(existing.getSyncStatus())) {
            existing.setSyncStatus(1);
            changed = true;
        }
        if (changed) {
            existing.setSyncTime(syncTime);
            existing.setUpdateTime(syncTime);
            existing.setUpdateUser(operatorId);
        }
        return changed;
    }

    /**
     * API 侧真实邮箱：email → biz_mail → null
     */
    String resolveSyncEmail(WecomUserDTO wecomUser) {
        if (wecomUser == null) {
            return null;
        }
        String email = trimToNull(wecomUser.getEmail());
        if (email != null) {
            return email;
        }
        return trimToNull(wecomUser.getBizMail());
    }

    String resolveCreateEmail(WecomUserDTO wecomUser, String organizationId, String excludeUserId, SyncPartResult result) {
        String apiEmail = resolveSyncEmail(wecomUser);
        if (apiEmail == null) {
            return OrgSyncConstants.buildPlaceholderEmail(wecomUser.getUserid());
        }
        if (apiEmail.length() > OrgSyncConstants.MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("邮箱长度超过 " + OrgSyncConstants.MAX_EMAIL_LENGTH + ": " + apiEmail);
        }
        User occupied = orgSyncEmailConflictService.findOtherByEmail(apiEmail, excludeUserId);
        if (occupied != null) {
            result.setEmailConflict(result.getEmailConflict() + 1);
            orgSyncEmailConflictService.registerConflict(organizationId, null, wecomUser.getUserid().trim(),
                    excludeUserId, trimToNull(wecomUser.getName()), apiEmail, occupied,
                    OrgSyncConstants.EMAIL_CONFLICT_SCENE_CREATE);
            return OrgSyncConstants.buildPlaceholderEmail(wecomUser.getUserid());
        }
        return apiEmail;
    }

    boolean applyEmailOnUpdate(User existing, WecomUserDTO wecomUser, String organizationId, SyncPartResult result) {
        String apiEmail = resolveSyncEmail(wecomUser);
        if (apiEmail == null) {
            return false;
        }
        if (apiEmail.length() > OrgSyncConstants.MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("邮箱长度超过 " + OrgSyncConstants.MAX_EMAIL_LENGTH + ": " + apiEmail);
        }
        if (StringUtils.equalsIgnoreCase(apiEmail, existing.getEmail())) {
            return false;
        }
        User occupied = orgSyncEmailConflictService.findOtherByEmail(apiEmail, existing.getId());
        if (occupied != null) {
            result.setEmailConflict(result.getEmailConflict() + 1);
            orgSyncEmailConflictService.registerConflict(organizationId, null, wecomUser.getUserid().trim(),
                    existing.getId(), trimToNull(wecomUser.getName()), apiEmail, occupied,
                    OrgSyncConstants.EMAIL_CONFLICT_SCENE_UPDATE);
            return false;
        }
        existing.setEmail(apiEmail);
        return true;
    }

    private void collectMissingStats(WecomUserDTO wecomUser, SyncPartResult result) {
        if (trimToNull(wecomUser.getMobile()) == null) {
            result.setMissingMobile(result.getMissingMobile() + 1);
        }
        if (resolveSyncEmail(wecomUser) == null) {
            result.setMissingEmail(result.getMissingEmail() + 1);
        }
    }

    private void logFieldSample(List<WecomUserDTO> wecomUsers) {
        int n = Math.min(20, wecomUsers.size());
        if (n == 0) {
            return;
        }
        int mobile = 0;
        int email = 0;
        int bizMail = 0;
        int mainDept = 0;
        for (int i = 0; i < n; i++) {
            WecomUserDTO u = wecomUsers.get(i);
            if (u == null) {
                continue;
            }
            if (trimToNull(u.getMobile()) != null) {
                mobile++;
            }
            if (trimToNull(u.getEmail()) != null) {
                email++;
            }
            if (trimToNull(u.getBizMail()) != null) {
                bizMail++;
            }
            if (u.getMainDepartment() != null) {
                mainDept++;
            }
        }
        LogUtils.info("企微成员字段抽样(前{}): mobile={}/{}, email={}/{}, biz_mail={}/{}, main_department={}/{}",
                n, mobile, n, email, n, bizMail, n, mainDept, n);
    }

    private void ensureOrganizationMember(String userId, String organizationId, String operatorId) {
        OrganizationMemberRequest request = new OrganizationMemberRequest();
        request.setOrganizationId(organizationId);
        request.setUserIds(List.of(userId));
        request.setUserRoleIds(List.of(InternalUserRole.ORG_MEMBER.getValue()));
        organizationService.addMemberBySystem(request, operatorId);
    }

    String resolveMainDepartment(WecomUserDTO wecomUser, Map<Long, Department> wecomDeptMap) {
        if (wecomUser == null) {
            return null;
        }
        Long wecomDeptId = wecomUser.getMainDepartment();
        if (wecomDeptId == null && CollectionUtils.isNotEmpty(wecomUser.getDepartment())) {
            wecomDeptId = wecomUser.getDepartment().getFirst();
        }
        if (wecomDeptId != null) {
            Department department = wecomDeptMap.get(wecomDeptId);
            if (department != null) {
                return department.getId();
            }
        }
        if (CollectionUtils.isNotEmpty(wecomUser.getDepartment())) {
            for (Long deptId : wecomUser.getDepartment()) {
                Department department = wecomDeptMap.get(deptId);
                if (department != null) {
                    return department.getId();
                }
            }
        }
        return null;
    }

    private Boolean mapEnable(Integer wecomStatus) {
        if (wecomStatus == null) {
            return false;
        }
        return wecomStatus == 1;
    }

    private boolean isProtectedUser(User user) {
        if (user == null) {
            return true;
        }
        if (OrgSyncConstants.PROTECTED_USER_ID.equals(user.getId())) {
            return true;
        }
        return StringUtils.isNotBlank(user.getWecomUserid())
                && user.getWecomUserid().trim().startsWith(OrgSyncConstants.SYSTEM_ACCOUNT_PREFIX);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
