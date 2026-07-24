package io.metersphere.system.edit.service;

import io.metersphere.sdk.constants.ResourceEditConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.SystemParameter;
import io.metersphere.system.edit.ResourceEditAdapter;
import io.metersphere.system.edit.ResourceEditAdapterRegistry;
import io.metersphere.system.edit.dao.ResourceEditLockDao;
import io.metersphere.system.edit.dao.ResourceEditSnapshotDao;
import io.metersphere.system.edit.dto.*;
import io.metersphere.system.mapper.SystemParameterMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 资源编辑锁 / 快照 / Undo·Redo
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class ResourceEditService {

    @Resource
    private ResourceEditLockDao lockDao;
    @Resource
    private ResourceEditSnapshotDao snapshotDao;
    @Resource
    private ResourceEditAdapterRegistry adapterRegistry;
    @Resource
    private SystemParameterMapper systemParameterMapper;

    public boolean isAutosaveEnabled() {
        SystemParameter param = systemParameterMapper.selectByPrimaryKey(ResourceEditConstants.AUTOSAVE_ENABLED_PARAM_KEY);
        if (param == null || StringUtils.isBlank(param.getParamValue())) {
            return true;
        }
        return !StringUtils.equalsAnyIgnoreCase(param.getParamValue(), "false", "0", "off", "disabled");
    }

    /** 写入路径单次快照开关，缺省关闭 */
    public boolean isWritePathSnapshotEnabled() {
        SystemParameter param = systemParameterMapper.selectByPrimaryKey(ResourceEditConstants.WRITE_PATH_SNAPSHOT_ENABLED_PARAM_KEY);
        if (param == null || StringUtils.isBlank(param.getParamValue())) {
            return false;
        }
        return StringUtils.equalsAnyIgnoreCase(param.getParamValue(), "true", "1", "on", "enabled");
    }

    /**
     * Agent / 导入 / 批量等写入成功后可选登记 1 次快照（不要求持有编辑锁）。
     * 与人工自动保存共用滚动窗口；内容未变（hash 相同）则跳过。
     */
    public void recordWritePathSnapshot(String resourceType, String resourceId, String projectId, String userId) {
        if (!isWritePathSnapshotEnabled()) {
            return;
        }
        ResourceEditAdapter adapter = adapterRegistry.get(resourceType);
        if (adapter == null || StringUtils.isAnyBlank(resourceType, resourceId, projectId)) {
            return;
        }
        try {
            String payload = adapter.loadPayload(resourceId);
            if (StringUtils.isBlank(payload)) {
                return;
            }
            ResourceEditSnapshotRequest request = new ResourceEditSnapshotRequest();
            request.setResourceType(resourceType);
            request.setResourceId(resourceId);
            request.setProjectId(projectId);
            request.setPayload(payload);
            writeSnapshotWithoutLock(request, userId);
        } catch (Exception ignored) {
            // 写入路径快照失败不阻断主流程
        }
    }

    public void recordWritePathSnapshots(String resourceType, String projectId, List<String> resourceIds, String userId) {
        if (!isWritePathSnapshotEnabled() || resourceIds == null) {
            return;
        }
        for (String id : resourceIds) {
            recordWritePathSnapshot(resourceType, id, projectId, userId);
        }
    }

    public ResourceEditLockResponse acquire(ResourceEditLockRequest request, String userId, String userName) {
        lockDao.deleteExpired(System.currentTimeMillis());
        Map<String, Object> existing = lockDao.find(request.getResourceType(), request.getResourceId());
        long now = System.currentTimeMillis();
        long expire = now + ResourceEditConstants.LOCK_TTL_MS;

        if (existing != null) {
            long exp = ((Number) existing.get("expire_time")).longValue();
            String holder = (String) existing.get("holder_user_id");
            if (exp >= now && !StringUtils.equals(holder, userId)) {
                ResourceEditLockResponse resp = new ResourceEditLockResponse();
                resp.setAcquired(false);
                resp.setReadOnly(true);
                resp.setHolderUserId(holder);
                resp.setHolderUserName((String) existing.get("holder_user_name"));
                resp.setExpireTime(exp);
                resp.setMessage(StringUtils.defaultString(resp.getHolderUserName(), holder) + " 正在编辑");
                return resp;
            }
        }

        lockDao.upsert(request.getResourceType(), request.getResourceId(), request.getProjectId(),
                userId, userName, expire);
        // 基线快照：便于首次保存后可 Undo 回打开时状态
        tryRecordBaseline(request.getResourceType(), request.getResourceId(), request.getProjectId(), userId);

        ResourceEditLockResponse resp = new ResourceEditLockResponse();
        resp.setAcquired(true);
        resp.setReadOnly(false);
        resp.setHolderUserId(userId);
        resp.setHolderUserName(userName);
        resp.setExpireTime(expire);
        return resp;
    }

    public ResourceEditLockResponse heartbeat(ResourceEditLockRequest request, String userId, String userName) {
        lockDao.deleteExpired(System.currentTimeMillis());
        Map<String, Object> existing = lockDao.find(request.getResourceType(), request.getResourceId());
        ResourceEditLockResponse resp = new ResourceEditLockResponse();
        if (existing == null || !StringUtils.equals((String) existing.get("holder_user_id"), userId)) {
            resp.setAcquired(false);
            resp.setReadOnly(true);
            resp.setMessage("未持有编辑锁");
            return resp;
        }
        long expire = System.currentTimeMillis() + ResourceEditConstants.LOCK_TTL_MS;
        lockDao.upsert(request.getResourceType(), request.getResourceId(), request.getProjectId(),
                userId, userName, expire);
        resp.setAcquired(true);
        resp.setReadOnly(false);
        resp.setHolderUserId(userId);
        resp.setExpireTime(expire);
        return resp;
    }

    public void release(ResourceEditLockRequest request, String userId) {
        lockDao.delete(request.getResourceType(), request.getResourceId(), userId);
    }

    /** 业务保存成功后调用：截断 redo、写入新快照、滚动保留 */
    public void afterSuccessfulSave(ResourceEditSnapshotRequest request, String userId) {
        assertPayloadSize(request.getPayload());
        assertHolder(request.getResourceType(), request.getResourceId(), userId);
        writeSnapshotWithoutLock(request, userId);
    }

    private void writeSnapshotWithoutLock(ResourceEditSnapshotRequest request, String userId) {
        assertPayloadSize(request.getPayload());

        Long active = snapshotDao.getActiveSeq(request.getResourceType(), request.getResourceId());
        if (active != null) {
            snapshotDao.deleteGreaterThan(request.getResourceType(), request.getResourceId(), active);
        }
        String hash = sha256(request.getPayload());
        if (active != null) {
            Map<String, Object> cur = snapshotDao.findBySeq(request.getResourceType(), request.getResourceId(), active);
            if (cur != null && StringUtils.equals(hash, (String) cur.get("content_hash"))) {
                return;
            }
        }
        long seq = snapshotDao.nextSeq(request.getResourceType(), request.getResourceId());
        snapshotDao.insert(request.getResourceType(), request.getResourceId(), request.getProjectId(),
                seq, request.getPayload(), hash, userId);
        snapshotDao.setActiveSeq(request.getResourceType(), request.getResourceId(), seq);
        trimSnapshots(request.getResourceType(), request.getResourceId());
    }

    public ResourceEditUndoResponse undo(ResourceEditLockRequest request, String userId) {
        assertHolder(request.getResourceType(), request.getResourceId(), userId);
        ResourceEditMetaResponse meta = meta(request.getResourceType(), request.getResourceId());
        if (meta.getUndoAvailable() <= 0) {
            ResourceEditUndoResponse resp = new ResourceEditUndoResponse();
            resp.setSuccess(false);
            resp.setMessage("没有可撤销的变更");
            resp.setUndoAvailable(0);
            resp.setRedoAvailable(meta.getRedoAvailable());
            return resp;
        }
        List<Long> seqs = snapshotDao.listSeqAsc(request.getResourceType(), request.getResourceId());
        long active = meta.getActiveSeq();
        int idx = seqs.indexOf(active);
        long target = seqs.get(idx - 1);
        return applySeq(request, userId, target);
    }

    public ResourceEditUndoResponse redo(ResourceEditLockRequest request, String userId) {
        assertHolder(request.getResourceType(), request.getResourceId(), userId);
        ResourceEditMetaResponse meta = meta(request.getResourceType(), request.getResourceId());
        if (meta.getRedoAvailable() <= 0) {
            ResourceEditUndoResponse resp = new ResourceEditUndoResponse();
            resp.setSuccess(false);
            resp.setMessage("没有可重做的变更");
            resp.setUndoAvailable(meta.getUndoAvailable());
            resp.setRedoAvailable(0);
            return resp;
        }
        List<Long> seqs = snapshotDao.listSeqAsc(request.getResourceType(), request.getResourceId());
        long active = meta.getActiveSeq();
        int idx = seqs.indexOf(active);
        long target = seqs.get(idx + 1);
        return applySeq(request, userId, target);
    }

    public ResourceEditMetaResponse meta(String resourceType, String resourceId) {
        ResourceEditMetaResponse resp = new ResourceEditMetaResponse();
        resp.setResourceType(resourceType);
        resp.setResourceId(resourceId);
        List<Long> seqs = snapshotDao.listSeqAsc(resourceType, resourceId);
        Long active = snapshotDao.getActiveSeq(resourceType, resourceId);
        if (seqs.isEmpty() || active == null) {
            resp.setUndoAvailable(0);
            resp.setRedoAvailable(0);
            resp.setActiveSeq(active);
            return resp;
        }
        int idx = seqs.indexOf(active);
        if (idx < 0) {
            idx = seqs.size() - 1;
            active = seqs.get(idx);
            snapshotDao.setActiveSeq(resourceType, resourceId, active);
        }
        int undo = Math.min(idx, ResourceEditConstants.MAX_UNDO_STEPS);
        int redo = seqs.size() - 1 - idx;
        resp.setUndoAvailable(undo);
        resp.setRedoAvailable(redo);
        resp.setActiveSeq(active);
        return resp;
    }

    private ResourceEditUndoResponse applySeq(ResourceEditLockRequest request, String userId, long targetSeq) {
        Map<String, Object> snap = snapshotDao.findBySeq(request.getResourceType(), request.getResourceId(), targetSeq);
        if (snap == null) {
            throw new MSException("快照不存在");
        }
        String payload = (String) snap.get("payload");
        ResourceEditAdapter adapter = adapterRegistry.get(request.getResourceType());
        if (adapter == null) {
            throw new MSException("未注册资源适配器: " + request.getResourceType());
        }
        adapter.applyPayload(request.getResourceId(), payload, userId);
        snapshotDao.setActiveSeq(request.getResourceType(), request.getResourceId(), targetSeq);
        ResourceEditMetaResponse meta = meta(request.getResourceType(), request.getResourceId());
        ResourceEditUndoResponse resp = new ResourceEditUndoResponse();
        resp.setSuccess(true);
        resp.setPayload(payload);
        resp.setUndoAvailable(meta.getUndoAvailable());
        resp.setRedoAvailable(meta.getRedoAvailable());
        return resp;
    }

    private void tryRecordBaseline(String resourceType, String resourceId, String projectId, String userId) {
        ResourceEditAdapter adapter = adapterRegistry.get(resourceType);
        if (adapter == null) {
            return;
        }
        List<Long> seqs = snapshotDao.listSeqAsc(resourceType, resourceId);
        if (!seqs.isEmpty()) {
            return;
        }
        try {
            String payload = adapter.loadPayload(resourceId);
            if (StringUtils.isBlank(payload)) {
                return;
            }
            assertPayloadSize(payload);
            long seq = snapshotDao.nextSeq(resourceType, resourceId);
            snapshotDao.insert(resourceType, resourceId, projectId, seq, payload,
                    sha256(payload), userId);
            snapshotDao.setActiveSeq(resourceType, resourceId, seq);
        } catch (Exception ignored) {
            // 基线失败不阻断加锁
        }
    }

    private void trimSnapshots(String resourceType, String resourceId) {
        List<Long> seqs = snapshotDao.listSeqAsc(resourceType, resourceId);
        Long active = snapshotDao.getActiveSeq(resourceType, resourceId);
        while (seqs.size() > ResourceEditConstants.MAX_SNAPSHOTS_PER_RESOURCE) {
            long oldest = seqs.getFirst();
            // 不删 active 及之后（redo 链）；只从左侧删
            if (active != null && oldest >= active) {
                break;
            }
            snapshotDao.deleteBySeq(resourceType, resourceId, oldest);
            seqs.removeFirst();
        }
    }

    private void assertHolder(String resourceType, String resourceId, String userId) {
        lockDao.deleteExpired(System.currentTimeMillis());
        Map<String, Object> lock = lockDao.find(resourceType, resourceId);
        if (lock == null || !StringUtils.equals((String) lock.get("holder_user_id"), userId)) {
            throw new MSException("未持有编辑锁，无法操作");
        }
    }

    private void assertPayloadSize(String payload) {
        if (payload != null && payload.length() > ResourceEditConstants.MAX_PAYLOAD_CHARS) {
            throw new MSException("快照内容过大，无法保存版本");
        }
    }

    private String sha256(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return String.valueOf(payload.hashCode());
        }
    }
}
