package io.metersphere.functional.hub.service;

import io.metersphere.functional.constants.FunctionalCaseReviewStatus;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseBlob;
import io.metersphere.functional.hub.dao.DefaultHubCaseMapDao;
import io.metersphere.functional.hub.dto.DefaultHubCaseMapRow;
import io.metersphere.functional.mapper.FunctionalCaseBlobMapper;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.service.FunctionalCaseCustomFieldService;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.project.mapper.ExtBaseProjectVersionMapper;
import io.metersphere.sdk.constants.ApplicationNumScope;
import io.metersphere.sdk.constants.ExecStatus;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.service.DefaultHubProjectService;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.uid.NumGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * 业务用例 → 默认项目枢纽镜像同步
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class DefaultHubCaseSyncService {

    @Resource
    private DefaultHubConfigService defaultHubConfigService;
    @Resource
    private DefaultHubProjectService defaultHubProjectService;
    @Resource
    private DefaultHubCaseMapDao defaultHubCaseMapDao;
    @Resource
    private DefaultHubModuleResolver defaultHubModuleResolver;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private FunctionalCaseBlobMapper functionalCaseBlobMapper;
    @Resource
    private FunctionalCaseCustomFieldService functionalCaseCustomFieldService;
    @Lazy
    @Resource
    private FunctionalCaseService functionalCaseService;
    @Resource
    private ExtBaseProjectVersionMapper extBaseProjectVersionMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Lazy
    @Resource
    private DefaultHubCaseImportService attachmentCopier;

    /**
     * 业务用例 upsert 到枢纽；仅排除枢纽项目自身以防循环
     */
    public void syncCaseUpsert(String bizProjectId, String bizCaseId, String operator) {
        if (!defaultHubConfigService.isSyncEnabled()) {
            throw new io.metersphere.sdk.exception.MSException("资产持续同步已关闭，请核对系统配置后重试");
        }
        if (defaultHubProjectService.isDefaultProject(bizProjectId)) {
            return;
        }

        jdbcTemplate.queryForList("SELECT id FROM functional_case WHERE id=? AND project_id=? FOR UPDATE",bizCaseId,bizProjectId);
        FunctionalCase bizCase = functionalCaseMapper.selectByPrimaryKey(bizCaseId);
        if (bizCase == null || Boolean.TRUE.equals(bizCase.getDeleted())) {
            syncCaseDelete(bizCaseId,operator);
            return;
        }
        if (!bizProjectId.equals(bizCase.getProjectId()) || !Boolean.TRUE.equals(bizCase.getLatest())) return;
        DefaultHubCaseMapRow map = defaultHubCaseMapDao.findByBizCaseId(bizCaseId);
        String sourceRefId=StringUtils.defaultIfBlank(bizCase.getRefId(),bizCaseId);
        List<String> historical=jdbcTemplate.queryForList("SELECT asset_case_id FROM case_asset_source_relation WHERE source_project_id=? AND source_case_id=?",String.class,bizProjectId,sourceRefId);
        if(map!=null && !historical.isEmpty() && !map.getHubCaseId().equals(historical.getFirst()))throw new io.metersphere.sdk.exception.MSException("同一源用例存在重复资产映射，请先核对引用并修复映射");
        if(map==null && !historical.isEmpty()) {
            var target=functionalCaseMapper.selectByPrimaryKey(historical.getFirst());
            if(target==null || !defaultHubProjectService.isDefaultProject(target.getProjectId()))throw new io.metersphere.sdk.exception.MSException("历史资产映射已失效，请核对来源");
            defaultHubCaseMapDao.insert(bizProjectId,bizCaseId,target.getId(),"");
            map=defaultHubCaseMapDao.findByBizCaseId(bizCaseId);
        }

        // 防环：更新路径仅处理已建立映射的用例；新建时 map 为空仍允许首次同步


        String hubProjectId = defaultHubProjectService.getDefaultProjectId();
        if (StringUtils.isBlank(hubProjectId)) {
            throw new io.metersphere.sdk.exception.MSException("资产存储项目未配置，请联系管理员");
        }
        String hubModuleId = defaultHubModuleResolver.resolveHubModuleId(bizProjectId, bizCase.getModuleId(), hubProjectId, operator);
        if (StringUtils.isBlank(hubModuleId)) {
            throw new io.metersphere.sdk.exception.MSException("项目资产目录尚未就绪，请修复目录后重试");
        }

        FunctionalCaseBlob blob = functionalCaseBlobMapper.selectByPrimaryKey(bizCaseId);
        String contentHash = computeHash(bizCase, blob);

        if (map == null) {
            String hubCaseId = createHubMirror(bizCase, blob, hubProjectId, hubModuleId, operator);
            defaultHubCaseMapDao.insert(bizProjectId, bizCaseId, hubCaseId, contentHash);
        } else {
            FunctionalCase hubExisting = functionalCaseMapper.selectByPrimaryKey(map.getHubCaseId());
            if(hubExisting==null)throw new io.metersphere.sdk.exception.MSException("资产镜像已被彻底删除，请先修复来源映射");
            boolean moduleChanged = hubExisting != null && !StringUtils.equals(hubExisting.getModuleId(), hubModuleId);
            if (!StringUtils.equals(contentHash, map.getContentHash()) || moduleChanged || hubExisting == null || Boolean.TRUE.equals(hubExisting.getDeleted())) {
                updateHubMirror(map.getHubCaseId(), bizCase, blob, hubModuleId, operator);
                defaultHubCaseMapDao.updateHash(bizCaseId, contentHash);
            }
        }
        DefaultHubCaseMapRow current=defaultHubCaseMapDao.findByBizCaseId(bizCaseId);
        // Selective mapper updates cannot clear a previous executor/time when the source resets.
        if(current!=null)jdbcTemplate.update("UPDATE functional_case SET review_status=?,last_execute_result=?,execute_user=?,last_execute_user=?,last_execute_time=? WHERE id=?",
                bizCase.getReviewStatus(),bizCase.getLastExecuteResult(),bizCase.getExecuteUser(),bizCase.getLastExecuteUser(),bizCase.getLastExecuteTime(),current.getHubCaseId());
        if(current!=null)jdbcTemplate.update("INSERT INTO case_asset_source_relation(id,asset_case_id,source_project_id,source_case_id,source_update_time,create_user,create_time,update_time) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE asset_case_id=VALUES(asset_case_id),source_update_time=VALUES(source_update_time),update_time=VALUES(update_time)",
                IDGenerator.nextStr(),current.getHubCaseId(),bizProjectId,sourceRefId,bizCase.getUpdateTime(),operator,System.currentTimeMillis(),System.currentTimeMillis());
    }

    /** 业务用例删除：镜像进回收站，保留来源身份以支持恢复。 */
    public void syncCaseDelete(String bizCaseId, String operator) {
        if (!defaultHubConfigService.isSyncEnabled()) {
            throw new io.metersphere.sdk.exception.MSException("资产持续同步已关闭，请核对系统配置后重试");
        }
        DefaultHubCaseMapRow map = defaultHubCaseMapDao.findByBizCaseId(bizCaseId);
        if (map == null) {
            return;
        }
        // A delete event for an older physical version must not tombstone the current source mirror.
        List<String> latest=jdbcTemplate.queryForList("SELECT f.id FROM case_asset_source_relation r JOIN functional_case f ON f.project_id=r.source_project_id AND COALESCE(NULLIF(f.ref_id,''),f.id)=r.source_case_id WHERE r.asset_case_id=? AND r.source_project_id=? AND f.deleted=0 AND f.latest=1 ORDER BY f.id LIMIT 2",String.class,map.getHubCaseId(),map.getBizProjectId());
        if(latest.size()>1)throw new io.metersphere.sdk.exception.MSException("来源存在多个最新版本，请先修复版本状态");
        if(!latest.isEmpty()&&!latest.getFirst().equals(bizCaseId)){syncCaseUpsert(map.getBizProjectId(),latest.getFirst(),operator);return;}
        FunctionalCase hubCase = new FunctionalCase();
        hubCase.setId(map.getHubCaseId());
        hubCase.setDeleted(true);
        hubCase.setDeleteUser(operator);
        hubCase.setDeleteTime(System.currentTimeMillis());
        functionalCaseMapper.updateByPrimaryKeySelective(hubCase);
        // Retain source identity so restoration updates the same asset instead of creating a duplicate.
    }

    public void reconcileProjectCases(String bizProjectId, String operator, ProgressCallback callback) {
        if (defaultHubProjectService.isDefaultProject(bizProjectId)) {
            return;
        }
        List<String> allBizCaseIds = jdbcTemplate.queryForList(
                "SELECT id FROM functional_case WHERE project_id = ? AND deleted = 0 AND latest=1",
                String.class, bizProjectId);
        int total = allBizCaseIds.size();
        int done = 0;
        int success = 0;
        for (String caseId : allBizCaseIds) {
            try {
                syncCaseUpsert(bizProjectId, caseId, operator);
                success++;
            } catch (Exception e) {
                LogUtils.error("hub case reconcile failed: " + caseId, e);
            }
            done++;
            if (callback != null) {
                callback.onProgress(done, total, success, done - success);
            }
        }
        // 清理孤儿映射
        for (DefaultHubCaseMapRow map : defaultHubCaseMapDao.listByBizProjectId(bizProjectId)) {
            FunctionalCase biz = functionalCaseMapper.selectByPrimaryKey(map.getBizCaseId());
            if (biz == null || Boolean.TRUE.equals(biz.getDeleted())) {
                syncCaseDelete(map.getBizCaseId(), operator);
            }
        }
    }

    private String createHubMirror(FunctionalCase src, FunctionalCaseBlob blob, String hubProjectId, String hubModuleId, String operator) {
        String hubCaseId = IDGenerator.nextStr();
        FunctionalCase hub = copyCaseHeader(src, hubCaseId, hubProjectId, hubModuleId, operator);
        hub.setNum(NumGenerator.nextNum(hubProjectId, ApplicationNumScope.CASE_MANAGEMENT));
        hub.setPos(functionalCaseService.getNextOrder(hubProjectId));
        functionalCaseMapper.insertSelective(hub);
        insertBlob(hubCaseId, blob);
        functionalCaseCustomFieldService.copyCustomField(src.getId(), hubCaseId);
        attachmentCopier.copyAttachments(src.getProjectId(),src.getId(),hubProjectId,hubCaseId,operator,true);
        return hubCaseId;
    }

    private void updateHubMirror(String hubCaseId, FunctionalCase src, FunctionalCaseBlob blob, String hubModuleId, String operator) {
        String hubProjectId = defaultHubProjectService.getDefaultProjectId();
        FunctionalCase hub = copyCaseHeader(src, hubCaseId, hubProjectId, hubModuleId, operator);
        hub.setCreateTime(null);
        hub.setCreateUser(null);
        functionalCaseMapper.updateByPrimaryKeySelective(hub);
        FunctionalCaseBlob hubBlob = new FunctionalCaseBlob();
        hubBlob.setId(hubCaseId);
        if (blob != null) {
            hubBlob.setSteps(blob.getSteps());
            hubBlob.setTextDescription(blob.getTextDescription());
            hubBlob.setExpectedResult(blob.getExpectedResult());
            hubBlob.setPrerequisite(blob.getPrerequisite());
            hubBlob.setDescription(blob.getDescription());
        }
        functionalCaseBlobMapper.updateByPrimaryKeyWithBLOBs(hubBlob);
        functionalCaseCustomFieldService.copyCustomField(src.getId(), hubCaseId);
        attachmentCopier.copyAttachments(src.getProjectId(),src.getId(),hubProjectId,hubCaseId,operator,true);
    }

    private FunctionalCase copyCaseHeader(FunctionalCase src, String id, String projectId, String moduleId, String operator) {
        FunctionalCase c = new FunctionalCase();
        c.setId(id);
        c.setProjectId(projectId);
        c.setModuleId(moduleId);
        c.setTemplateId(src.getTemplateId());
        c.setName(src.getName());
        c.setTags(src.getTags());
        c.setCaseEditType(src.getCaseEditType());
        c.setVersionId(extBaseProjectVersionMapper.getDefaultVersion(projectId));
        c.setRefId(id);
        c.setReviewStatus(src.getReviewStatus());
        c.setLastExecuteResult(src.getLastExecuteResult());
        c.setExecuteUser(src.getExecuteUser());
        c.setLastExecuteUser(src.getLastExecuteUser());
        c.setLastExecuteTime(src.getLastExecuteTime());
        c.setDeleted(false);
        c.setAiCreate(false);
        c.setPublicCase(false);
        c.setLatest(true);
        c.setCreateUser(operator);
        c.setUpdateUser(operator);
        long now = System.currentTimeMillis();
        c.setCreateTime(now);
        c.setUpdateTime(now);
        return c;
    }

    private void insertBlob(String caseId, FunctionalCaseBlob src) {
        FunctionalCaseBlob blob = new FunctionalCaseBlob();
        blob.setId(caseId);
        if (src != null) {
            blob.setSteps(src.getSteps());
            blob.setTextDescription(src.getTextDescription());
            blob.setExpectedResult(src.getExpectedResult());
            blob.setPrerequisite(src.getPrerequisite());
            blob.setDescription(src.getDescription());
        } else {
            blob.setSteps(new byte[0]);
            blob.setTextDescription(new byte[0]);
            blob.setExpectedResult(new byte[0]);
            blob.setPrerequisite(new byte[0]);
            blob.setDescription(new byte[0]);
        }
        functionalCaseBlobMapper.insertSelective(blob);
    }

    String computeHash(FunctionalCase c, FunctionalCaseBlob blob) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getName()).append('|').append(c.getModuleId()).append('|').append(c.getCaseEditType()).append('|').append(c.getTags());
        sb.append('|').append(c.getReviewStatus()).append('|').append(c.getLastExecuteResult())
                .append('|').append(c.getExecuteUser()).append('|').append(c.getLastExecuteUser()).append('|').append(c.getLastExecuteTime());
        sb.append(io.metersphere.sdk.util.JSON.toJSONString(jdbcTemplate.queryForList("SELECT field_id,value FROM functional_case_custom_field WHERE case_id=? ORDER BY field_id",c.getId())));
        sb.append(io.metersphere.sdk.util.JSON.toJSONString(jdbcTemplate.queryForList("SELECT file_id,file_name FROM functional_case_attachment WHERE case_id=? ORDER BY id",c.getId())));
        sb.append(io.metersphere.sdk.util.JSON.toJSONString(jdbcTemplate.queryForList("SELECT file_id,deleted FROM file_association WHERE source_id=? ORDER BY id",c.getId())));
        if (blob != null) {
            sb.append('|').append(bytes(blob.getSteps())).append(bytes(blob.getTextDescription()))
                    .append(bytes(blob.getExpectedResult())).append(bytes(blob.getPrerequisite()))
                    .append(bytes(blob.getDescription()));
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return sb.toString();
        }
    }

    private String bytes(byte[] data) {
        return data == null ? "" : new String(data, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int done, int total, int success, int fail);
    }
}
