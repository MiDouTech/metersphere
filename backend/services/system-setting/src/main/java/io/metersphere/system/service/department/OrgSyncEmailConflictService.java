package io.metersphere.system.service.department;

import io.metersphere.sdk.constants.HttpMethodConstants;
import io.metersphere.sdk.constants.OperationLogConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.domain.User;
import io.metersphere.system.domain.UserExample;
import io.metersphere.system.dto.builder.LogDTOBuilder;
import io.metersphere.system.dto.department.OrgSyncEmailConflictDTO;
import io.metersphere.system.log.constants.OperationLogModule;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.log.dto.LogDTO;
import io.metersphere.system.log.service.OperationLogService;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrgSyncEmailConflictService {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private UserMapper userMapper;
    @Resource
    private OperationLogService operationLogService;

    public void registerConflict(String organizationId, String syncLogId, String wecomUserid, String pendingUserId,
                                 String wecomUserName, String conflictEmail, User occupied,
                                 String conflictScene) {
        if (StringUtils.isAnyBlank(organizationId, wecomUserid, conflictEmail) || occupied == null) {
            return;
        }
        List<String> existing = jdbcTemplate.queryForList(
                "SELECT id FROM org_sync_email_conflict WHERE organization_id = ? AND wecom_userid = ? " +
                        "AND conflict_email = ? AND status = ? LIMIT 1",
                String.class, organizationId, wecomUserid, conflictEmail, OrgSyncConstants.EMAIL_CONFLICT_PENDING);
        long now = System.currentTimeMillis();
        if (CollectionUtils.isNotEmpty(existing)) {
            jdbcTemplate.update(
                    "UPDATE org_sync_email_conflict SET pending_user_id = ?, wecom_user_name = ?, occupied_user_id = ?, " +
                            "occupied_user_name = ?, conflict_scene = ?, sync_log_id = ?, update_time = ? WHERE id = ?",
                    pendingUserId, wecomUserName, occupied.getId(), occupied.getName(), conflictScene,
                    syncLogId, now, existing.getFirst());
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO org_sync_email_conflict (id, organization_id, sync_log_id, wecom_userid, pending_user_id, " +
                        "wecom_user_name, conflict_email, occupied_user_id, occupied_user_name, conflict_scene, status, " +
                        "create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                IDGenerator.nextStr(), organizationId, syncLogId, wecomUserid, pendingUserId, wecomUserName,
                conflictEmail, occupied.getId(), occupied.getName(), conflictScene,
                OrgSyncConstants.EMAIL_CONFLICT_PENDING, now, now);
    }

    public OrgSyncEmailConflictDTO getById(String conflictId) {
        List<OrgSyncEmailConflictDTO> rows = jdbcTemplate.query(
                "SELECT id, organization_id, wecom_userid, pending_user_id, wecom_user_name, conflict_email, " +
                        "occupied_user_id, occupied_user_name, conflict_scene, status, create_time " +
                        "FROM org_sync_email_conflict WHERE id = ? LIMIT 1",
                (rs, rowNum) -> mapConflict(rs), conflictId);
        return CollectionUtils.isEmpty(rows) ? null : rows.getFirst();
    }

    private OrgSyncEmailConflictDTO mapConflict(java.sql.ResultSet rs) throws java.sql.SQLException {
        OrgSyncEmailConflictDTO dto = new OrgSyncEmailConflictDTO();
        dto.setId(rs.getString("id"));
        dto.setOrganizationId(rs.getString("organization_id"));
        dto.setWecomUserid(rs.getString("wecom_userid"));
        dto.setPendingUserId(rs.getString("pending_user_id"));
        dto.setWecomUserName(rs.getString("wecom_user_name"));
        dto.setConflictEmail(rs.getString("conflict_email"));
        dto.setOccupiedUserId(rs.getString("occupied_user_id"));
        dto.setOccupiedUserName(rs.getString("occupied_user_name"));
        dto.setConflictScene(rs.getString("conflict_scene"));
        dto.setStatus(rs.getString("status"));
        dto.setCreateTime(rs.getLong("create_time"));
        return dto;
    }

    public List<OrgSyncEmailConflictDTO> listPending(String organizationId) {
        return jdbcTemplate.query(
                "SELECT id, organization_id, wecom_userid, pending_user_id, wecom_user_name, conflict_email, " +
                        "occupied_user_id, occupied_user_name, conflict_scene, status, create_time " +
                        "FROM org_sync_email_conflict WHERE organization_id = ? AND status = ? ORDER BY create_time DESC",
                (rs, rowNum) -> mapConflict(rs),
                organizationId, OrgSyncConstants.EMAIL_CONFLICT_PENDING);
    }

    public int countPending(String organizationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM org_sync_email_conflict WHERE organization_id = ? AND status = ?",
                Integer.class, organizationId, OrgSyncConstants.EMAIL_CONFLICT_PENDING);
        return count == null ? 0 : count;
    }

    @Transactional(rollbackFor = Exception.class)
    public void resolve(String conflictId, String action, String operatorId) {
        List<OrgSyncEmailConflictDTO> rows = jdbcTemplate.query(
                "SELECT id, organization_id, wecom_userid, pending_user_id, wecom_user_name, conflict_email, " +
                        "occupied_user_id, occupied_user_name, conflict_scene, status, create_time " +
                        "FROM org_sync_email_conflict WHERE id = ? LIMIT 1",
                (rs, rowNum) -> {
                    OrgSyncEmailConflictDTO dto = new OrgSyncEmailConflictDTO();
                    dto.setId(rs.getString("id"));
                    dto.setOrganizationId(rs.getString("organization_id"));
                    dto.setWecomUserid(rs.getString("wecom_userid"));
                    dto.setPendingUserId(rs.getString("pending_user_id"));
                    dto.setConflictEmail(rs.getString("conflict_email"));
                    dto.setOccupiedUserId(rs.getString("occupied_user_id"));
                    dto.setConflictScene(rs.getString("conflict_scene"));
                    dto.setStatus(rs.getString("status"));
                    return dto;
                }, conflictId);
        if (CollectionUtils.isEmpty(rows)) {
            throw new MSException("邮箱冲突记录不存在");
        }
        OrgSyncEmailConflictDTO conflict = rows.getFirst();
        if (!OrgSyncConstants.EMAIL_CONFLICT_PENDING.equals(conflict.getStatus())) {
            throw new MSException("冲突已处理");
        }
        String normalized = StringUtils.upperCase(StringUtils.trim(action));
        if (OrgSyncConstants.EMAIL_CONFLICT_SKIP.equals(normalized)) {
            markResolved(conflictId, OrgSyncConstants.EMAIL_CONFLICT_SKIP, operatorId);
            addResolveLog(conflict, OrgSyncConstants.EMAIL_CONFLICT_SKIP, operatorId);
            return;
        }
        if (OrgSyncConstants.EMAIL_CONFLICT_CREATE.equals(normalized)) {
            if (!OrgSyncConstants.EMAIL_CONFLICT_SCENE_CREATE.equals(conflict.getConflictScene())) {
                throw new MSException("用户已存在，请选择跳过或覆盖");
            }
            markResolved(conflictId, OrgSyncConstants.EMAIL_CONFLICT_CREATE, operatorId);
            addResolveLog(conflict, OrgSyncConstants.EMAIL_CONFLICT_CREATE, operatorId);
            return;
        }
        if (OrgSyncConstants.EMAIL_CONFLICT_OVERWRITE.equals(normalized)) {
            overwriteEmail(conflict, operatorId);
            markResolved(conflictId, OrgSyncConstants.EMAIL_CONFLICT_OVERWRITE, operatorId);
            addResolveLog(conflict, OrgSyncConstants.EMAIL_CONFLICT_OVERWRITE, operatorId);
            return;
        }
        throw new MSException("不支持的处理动作: " + action);
    }

    private void addResolveLog(OrgSyncEmailConflictDTO conflict, String resolution, String operatorId) {
        LogDTO logDTO = LogDTOBuilder.builder()
                .projectId(OperationLogConstants.ORGANIZATION)
                .organizationId(conflict.getOrganizationId())
                .sourceId(conflict.getId())
                .createUser(operatorId)
                .type(OperationLogType.UPDATE.name())
                .module(OperationLogModule.SETTING_ORGANIZATION_MEMBER)
                .method(HttpMethodConstants.POST.name())
                .path("/org-wecom/email-conflict/resolve")
                .content("处理企微同步邮箱冲突: " + resolution + ", email=" + conflict.getConflictEmail()
                        + ", wecomUserid=" + conflict.getWecomUserid())
                .build()
                .getLogDTO();
        operationLogService.add(logDTO);
    }

    private void overwriteEmail(OrgSyncEmailConflictDTO conflict, String operatorId) {
        User pending = resolvePendingUser(conflict);
        if (pending == null) {
            throw new MSException("待赋权用户不存在，请重新同步后再处理");
        }
        User occupied = userMapper.selectByPrimaryKey(conflict.getOccupiedUserId());
        if (occupied == null || Boolean.TRUE.equals(occupied.getDeleted())) {
            throw new MSException("占用方用户不存在，请刷新冲突列表");
        }
        if (!StringUtils.equalsIgnoreCase(occupied.getEmail(), conflict.getConflictEmail())) {
            throw new MSException("占用关系已变化，请刷新后重试");
        }
        String occupiedPlaceholder = uniquePlaceholder(occupied);
        User occupiedUpdate = new User();
        occupiedUpdate.setId(occupied.getId());
        occupiedUpdate.setEmail(occupiedPlaceholder);
        occupiedUpdate.setUpdateUser(operatorId);
        occupiedUpdate.setUpdateTime(System.currentTimeMillis());
        userMapper.updateByPrimaryKeySelective(occupiedUpdate);

        User pendingUpdate = new User();
        pendingUpdate.setId(pending.getId());
        pendingUpdate.setEmail(conflict.getConflictEmail());
        pendingUpdate.setUpdateUser(operatorId);
        pendingUpdate.setUpdateTime(System.currentTimeMillis());
        userMapper.updateByPrimaryKeySelective(pendingUpdate);
        LogUtils.info("email conflict overwrite: pending={}, occupied={}, email={}",
                pending.getId(), occupied.getId(), conflict.getConflictEmail());
    }

    private User resolvePendingUser(OrgSyncEmailConflictDTO conflict) {
        if (StringUtils.isNotBlank(conflict.getPendingUserId())) {
            return userMapper.selectByPrimaryKey(conflict.getPendingUserId());
        }
        UserExample example = new UserExample();
        example.createCriteria().andWecomUseridEqualTo(conflict.getWecomUserid()).andDeletedEqualTo(false);
        List<User> users = userMapper.selectByExample(example);
        return CollectionUtils.isEmpty(users) ? null : users.getFirst();
    }

    private String uniquePlaceholder(User occupied) {
        String base = OrgSyncConstants.buildPlaceholderEmail(
                StringUtils.defaultIfBlank(occupied.getWecomUserid(), occupied.getId()));
        if (findOtherByEmail(base, occupied.getId()) == null) {
            return base;
        }
        return occupied.getId().toLowerCase() + "." + System.currentTimeMillis()
                + OrgSyncConstants.WECOM_SYNC_EMAIL_SUFFIX;
    }

    public User findOtherByEmail(String email, String excludeUserId) {
        if (StringUtils.isBlank(email)) {
            return null;
        }
        UserExample example = new UserExample();
        example.createCriteria().andEmailEqualTo(email).andDeletedEqualTo(false);
        List<User> users = userMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(users)) {
            return null;
        }
        return users.stream()
                .filter(u -> !StringUtils.equals(u.getId(), excludeUserId))
                .findFirst()
                .orElse(null);
    }

    private void markResolved(String conflictId, String resolution, String operatorId) {
        jdbcTemplate.update(
                "UPDATE org_sync_email_conflict SET status = ?, resolution = ?, resolved_by = ?, resolved_time = ?, update_time = ? WHERE id = ?",
                OrgSyncConstants.EMAIL_CONFLICT_RESOLVED, resolution, operatorId,
                System.currentTimeMillis(), System.currentTimeMillis(), conflictId);
    }
}
