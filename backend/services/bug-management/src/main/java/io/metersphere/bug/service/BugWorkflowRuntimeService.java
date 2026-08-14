package io.metersphere.bug.service;

import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.dto.request.BugTransitionRequest;
import io.metersphere.bug.dto.response.BugTransitionDTO;
import io.metersphere.bug.enums.BugPlatform;
import io.metersphere.bug.mapper.BugMapper;
import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.service.PermissionControlService;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

@Service
@Transactional(rollbackFor = Exception.class)
public class BugWorkflowRuntimeService {
    @Resource
    private BugMapper bugMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PermissionControlService permissionControlService;
    @Resource
    private BugStatusService bugStatusService;
    @Lazy
    @Resource
    private BugService bugService;

    public void bindPublishedWorkflow(Bug bug) {
        if (bug == null) return;
        List<Map<String, Object>> flows = jdbcTemplate.queryForList("SELECT id, version FROM workflow_definition "
                + "WHERE scene = 'BUG' AND scope_type = 'SYSTEM' AND scope_id = 'system' "
                + "AND lifecycle = 'PUBLISHED' AND default_flow = b'1' AND enabled = b'1' LIMIT 1");
        if (flows.isEmpty()) throw new MSException("未发布全局缺陷流程，请先在权限控制/流程控制中发布流程");
        Map<String, Object> flow = flows.getFirst();
        String flowId = String.valueOf(flow.get("id"));
        List<Map<String, Object>> initial = jdbcTemplate.queryForList("SELECT id FROM status_item WHERE flow_id = ? "
                + "AND initial_status = b'1' AND enabled = b'1'", flowId);
        if (initial.size() != 1) throw new MSException("已发布全局缺陷流程的初始状态无效");
        bug.setWorkflowId(flowId);
        bug.setWorkflowVersion(((Number) flow.get("version")).intValue());
        if (StringUtils.equalsIgnoreCase(bug.getPlatform(), BugPlatform.LOCAL.getName())) {
            bug.setStatus(String.valueOf(initial.getFirst().get("id")));
        }
    }

    public BugTransitionDTO getTransitions(String bugId) {
        Bug bug = bugMapper.selectByPrimaryKey(bugId);
        if (bug == null || BooleanUtils.isTrue(bug.getDeleted())) {
            throw new MSException(MsHttpResultCode.NOT_FOUND, "缺陷不存在");
        }
        assertProjectPermission(bug, PermissionConstants.PROJECT_BUG_READ);
        return buildTransitions(bug, new HashMap<>());
    }

    private BugTransitionDTO buildTransitions(Bug bug, Map<String, List<Map<String, Object>>> edgeCache) {
        BugTransitionDTO result = new BugTransitionDTO();
        result.setBugId(bug.getId());
        result.setWorkflowId(bug.getWorkflowId());
        result.setWorkflowVersion(bug.getWorkflowVersion());
        result.setUpdateTime(bug.getUpdateTime());
        BugTransitionDTO.Status current = new BugTransitionDTO.Status();
        current.setId(bug.getStatus());
        current.setName(resolveStatusName(bug.getStatus()));
        result.setCurrentStatus(current);
        if (StringUtils.isAnyBlank(bug.getWorkflowId(), bug.getStatus())) {
            result.setUnavailableReason("历史缺陷尚未完成流程版本映射，请由管理员执行迁移");
            return result;
        }
        Integer flowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workflow_definition WHERE id = ? AND version = ? "
                        + "AND lifecycle IN ('PUBLISHED','ARCHIVED') AND enabled = b'1'", Integer.class,
                bug.getWorkflowId(), bug.getWorkflowVersion());
        if (flowCount == null || flowCount == 0) {
            result.setUnavailableReason("缺陷绑定的流程版本不存在或不可用");
            return result;
        }
        String sourceStatusId = resolveWorkflowStatusId(bug);
        if (StringUtils.isBlank(sourceStatusId)) {
            result.setUnavailableReason("第三方当前状态无法映射到全局流程状态");
            return result;
        }
        String edgeKey = bug.getWorkflowId() + ':' + sourceStatusId;
        List<Map<String, Object>> edges = edgeCache.computeIfAbsent(edgeKey, ignored -> jdbcTemplate.queryForList(
                "SELECT sf.id transition_id, sf.to_id, si.name target_name, si.status_code target_code "
                        + "FROM status_flow sf JOIN status_item si ON si.id = sf.to_id AND si.flow_id = sf.flow_id "
                        + "WHERE sf.flow_id = ? AND sf.from_id = ? AND sf.enabled = b'1' AND si.enabled = b'1'",
                bug.getWorkflowId(), sourceStatusId));
        Map<String, SelectOption> remoteAllowed = new HashMap<>();
        Map<String, SelectOption> remoteAllowedByName = new HashMap<>();
        if (!StringUtils.equalsIgnoreCase(bug.getPlatform(), BugPlatform.LOCAL.getName())) {
            List<SelectOption> remote = bugStatusService.getToStatusItemOption(bug.getProjectId(), bug.getStatus(),
                    bug.getPlatformBugId(), false);
            if (remote != null) remote.forEach(item -> {
                remoteAllowed.put(item.getValue(), item);
                remoteAllowedByName.put(StringUtils.lowerCase(item.getText()), item);
            });
        }
        boolean admin = permissionControlService.isCurrentUserAdmin();
        for (Map<String, Object> edge : edges) {
            String transitionId = String.valueOf(edge.get("transition_id"));
            String targetId = String.valueOf(edge.get("to_id"));
            SelectOption remoteTarget = null;
            if (!StringUtils.equalsIgnoreCase(bug.getPlatform(), BugPlatform.LOCAL.getName())) {
                remoteTarget = remoteAllowed.get(String.valueOf(edge.get("target_code")));
                if (remoteTarget == null) remoteTarget = remoteAllowedByName.get(StringUtils.lowerCase(String.valueOf(edge.get("target_name"))));
                if (remoteTarget == null) continue;
                targetId = remoteTarget.getValue();
            }
            List<String> visibleRoles = permissionControlService.matchWorkflowRoles(bug.getWorkflowId(), transitionId,
                    bug.getProjectId(), bug.getCreateUser(), bug.getHandleUser(), false);
            List<String> operableRoles = permissionControlService.matchWorkflowRoles(bug.getWorkflowId(), transitionId,
                    bug.getProjectId(), bug.getCreateUser(), bug.getHandleUser(), true);
            boolean overrideRequired = admin && operableRoles.isEmpty();
            BugTransitionDTO.Transition transition = new BugTransitionDTO.Transition();
            transition.setTransitionId(transitionId);
            BugTransitionDTO.Status target = new BugTransitionDTO.Status();
            target.setId(targetId);
            target.setName(remoteTarget == null ? String.valueOf(edge.get("target_name")) : remoteTarget.getText());
            transition.setTargetStatus(target);
            transition.setVisible(!visibleRoles.isEmpty() || admin);
            transition.setOperable(!operableRoles.isEmpty() || admin);
            transition.setOverrideRequired(overrideRequired);
            transition.setMatchedRoles(operableRoles);
            transition.setDisabledReason(transition.getOperable() ? null : "当前用户未命中该流转的可执行角色");
            if (BooleanUtils.isTrue(transition.getVisible())) result.getTransitions().add(transition);
        }
        return result;
    }

    public Map<String, BugTransitionDTO> getTransitions(List<String> bugIds) {
        Map<String, BugTransitionDTO> result = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(bugIds)) return result;
        List<String> ids = bugIds.stream().filter(StringUtils::isNotBlank).distinct().limit(200).toList();
        if (ids.isEmpty()) return result;
        io.metersphere.bug.domain.BugExample example = new io.metersphere.bug.domain.BugExample();
        example.createCriteria().andIdIn(ids).andDeletedEqualTo(false);
        Map<String, Bug> bugs = bugMapper.selectByExample(example).stream()
                .collect(java.util.stream.Collectors.toMap(Bug::getId, item -> item));
        Map<String, List<Map<String, Object>>> edgeCache = new HashMap<>();
        for (String id : ids) {
            Bug bug = bugs.get(id);
            if (bug == null || !SessionUtils.hasPermission(null, bug.getProjectId(), PermissionConstants.PROJECT_BUG_READ)) {
                continue;
            }
            result.put(id, buildTransitions(bug, edgeCache));
        }
        return result;
    }

    public BugTransitionDTO transition(String bugId, BugTransitionRequest request) {
        Bug bug = bugMapper.selectByPrimaryKey(bugId);
        if (bug == null || BooleanUtils.isTrue(bug.getDeleted())) {
            throw new MSException(MsHttpResultCode.NOT_FOUND, "缺陷不存在");
        }
        assertProjectPermission(bug, PermissionConstants.PROJECT_BUG_UPDATE);
        BugTransitionDTO runtime = getTransitions(bugId);
        BugTransitionDTO.Transition selected = runtime.getTransitions().stream()
                .filter(item -> StringUtils.equals(item.getTransitionId(), request.getTransitionId())
                        && StringUtils.equals(item.getTargetStatus().getId(), request.getTargetStatusId()))
                .findFirst().orElseThrow(() -> new MSException(MsHttpResultCode.CONFLICT, "目标状态不是当前状态的合法下一步"));
        if (!BooleanUtils.isTrue(selected.getOperable())) {
            throw new MSException(MsHttpResultCode.FORBIDDEN, "当前角色无缺陷状态流转权限");
        }
        boolean override = BooleanUtils.isTrue(selected.getOverrideRequired());
        if (override && (!BooleanUtils.isTrue(request.getOverride()) || StringUtils.isBlank(request.getOverrideReason()))) {
            throw new MSException(MsHttpResultCode.UNPROCESSABLE_ENTITY, "管理员绕过流程角色时必须填写原因");
        }
        long now = System.currentTimeMillis();
        if (!Objects.equals(bug.getUpdateTime(), request.getExpectedUpdateTime())) {
            throw new MSException(MsHttpResultCode.CONFLICT, "缺陷状态已变化，请刷新后重试");
        }
        if (StringUtils.equalsIgnoreCase(bug.getPlatform(), BugPlatform.LOCAL.getName())) {
            Boolean terminal = jdbcTemplate.queryForObject("SELECT terminal_status FROM status_item WHERE id=? AND flow_id=?",
                    Boolean.class, request.getTargetStatusId(), bug.getWorkflowId());
            Long closeTime = BooleanUtils.isTrue(terminal) ? now : null;
            int updated = jdbcTemplate.update("UPDATE bug SET `status` = ?, close_time = ?, update_user = ?, update_time = ? "
                            + "WHERE id = ? AND `status` = ? AND update_time = ? AND deleted = b'0'",
                    request.getTargetStatusId(), closeTime, SessionUtils.getUserId(), now, bugId, bug.getStatus(), request.getExpectedUpdateTime());
            if (updated != 1) throw new MSException(MsHttpResultCode.CONFLICT, "缺陷状态已变化，请刷新后重试");
        } else {
            bugService.transitionThirdPartyStatus(bug, request.getTargetStatusId(), SessionUtils.getUserId(),
                    SessionUtils.getCurrentOrganizationId());
            now = System.currentTimeMillis();
        }
        jdbcTemplate.update("INSERT INTO bug_status_transition_history "
                        + "(id, bug_id, workflow_id, workflow_version, transition_id, from_status_id, to_status_id, operator, "
                        + "matched_role_ids, comment, override_role, override_reason, create_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                IDGenerator.nextStr(), bugId, bug.getWorkflowId(), bug.getWorkflowVersion(), request.getTransitionId(),
                bug.getStatus(), request.getTargetStatusId(), SessionUtils.getUserId(), String.join(",", selected.getMatchedRoles()),
                request.getComment(), override, override ? request.getOverrideReason().trim() : null, now);
        return getTransitions(bugId);
    }

    public void recordTrustedThirdPartySync(Bug before, Bug after, String operator) {
        if (before == null || after == null || StringUtils.equals(before.getStatus(), after.getStatus())) return;
        jdbcTemplate.update("INSERT INTO bug_status_transition_history "
                        + "(id, bug_id, workflow_id, workflow_version, transition_id, source, from_status_id, to_status_id, "
                        + "operator, matched_role_ids, comment, override_role, override_reason, create_time) "
                        + "VALUES (?, ?, ?, ?, 'THIRD_PARTY_SYNC', 'THIRD_PARTY_SYNC', ?, ?, ?, '[]', ?, b'0', NULL, ?)",
                IDGenerator.nextStr(), before.getId(), StringUtils.defaultIfBlank(before.getWorkflowId(), "UNBOUND"),
                before.getWorkflowVersion() == null ? 0 : before.getWorkflowVersion(), before.getStatus(), after.getStatus(),
                StringUtils.defaultIfBlank(operator, "system"), "第三方平台状态同步", System.currentTimeMillis());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(String bugId) {
        Bug bug = bugMapper.selectByPrimaryKey(bugId);
        if (bug == null || BooleanUtils.isTrue(bug.getDeleted())) {
            throw new MSException(MsHttpResultCode.NOT_FOUND, "缺陷不存在");
        }
        assertProjectPermission(bug, PermissionConstants.PROJECT_BUG_READ);
        return new ArrayList<>(jdbcTemplate.queryForList("SELECT id, workflow_id workflowId, workflow_version workflowVersion, "
                + "transition_id transitionId, from_status_id fromStatusId, to_status_id toStatusId, operator, matched_role_ids matchedRoleIds, "
                + "comment, override_role overrideRole, override_reason overrideReason, create_time createTime "
                + "FROM bug_status_transition_history WHERE bug_id = ? ORDER BY create_time DESC", bugId));
    }

    private String resolveStatusName(String statusId) {
        if (StringUtils.isBlank(statusId)) return StringUtils.EMPTY;
        List<String> names = jdbcTemplate.query("SELECT name FROM status_item WHERE id = ?", (rs, rowNum) -> rs.getString(1), statusId);
        return names.isEmpty() ? statusId : names.getFirst();
    }

    private String resolveWorkflowStatusId(Bug bug) {
        if (StringUtils.equalsIgnoreCase(bug.getPlatform(), BugPlatform.LOCAL.getName())) return bug.getStatus();
        List<SelectOption> headers = bugStatusService.getHeaderStatusOption(bug.getProjectId());
        String currentName = headers.stream().filter(item -> StringUtils.equals(item.getValue(), bug.getStatus()))
                .map(SelectOption::getText).findFirst().orElse(null);
        List<String> ids = jdbcTemplate.query("SELECT id FROM status_item WHERE flow_id=? AND enabled=b'1' "
                        + "AND (status_code=? OR LOWER(name)=LOWER(?)) LIMIT 1",
                (rs, rowNum) -> rs.getString(1), bug.getWorkflowId(), bug.getStatus(), StringUtils.defaultString(currentName));
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private void assertProjectPermission(Bug bug, String permission) {
        if (!SessionUtils.hasPermission(null, bug.getProjectId(), permission)) {
            throw new MSException(MsHttpResultCode.FORBIDDEN, "无权访问当前缺陷所属项目");
        }
    }
}
