package io.metersphere.plan.hub.service;

import io.metersphere.functional.hub.service.DefaultHubConfigService;
import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.domain.TestPlanConfig;
import io.metersphere.plan.domain.TestPlanDocument;
import io.metersphere.plan.hub.dao.DefaultHubPlanMapDao;
import io.metersphere.plan.hub.dto.DefaultHubPlanMapRow;
import io.metersphere.plan.mapper.TestPlanConfigMapper;
import io.metersphere.plan.mapper.TestPlanDocumentMapper;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.plan.service.TestPlanService;
import io.metersphere.sdk.constants.ApplicationNumScope;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.constants.TestPlanConstants;
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
 * 业务测试计划 → 默认项目枢纽镜像（仅 test_plan + document）
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class DefaultHubPlanSyncService {

    @Resource
    private DefaultHubConfigService defaultHubConfigService;
    @Resource
    private DefaultHubProjectService defaultHubProjectService;
    @Resource
    private DefaultHubPlanMapDao defaultHubPlanMapDao;
    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private TestPlanConfigMapper testPlanConfigMapper;
    @Resource
    private TestPlanDocumentMapper testPlanDocumentMapper;
    @Lazy
    @Resource
    private TestPlanService testPlanService;
    @Resource
    private JdbcTemplate jdbcTemplate;

    public void syncPlanUpsert(String bizProjectId, String bizPlanId, String operator) {
        if (!defaultHubConfigService.isSyncEnabled()) {
            return;
        }
        if (defaultHubProjectService.isDefaultProject(bizProjectId)) {
            return;
        }
        if (isImportedFromHub(bizPlanId)) {
            return;
        }
        TestPlan bizPlan = testPlanMapper.selectByPrimaryKey(bizPlanId);
        if (bizPlan == null || !StringUtils.equals(bizPlan.getType(), TestPlanConstants.TEST_PLAN_TYPE_PLAN)) {
            return;
        }
        String hubProjectId = defaultHubProjectService.getDefaultProjectId();
        if (StringUtils.isBlank(hubProjectId)) {
            LogUtils.warn("default hub plan sync skipped, no default project");
            return;
        }
        String hubModuleId = resolveDefaultPlanModule(hubProjectId);
        String contentHash = computeHash(bizPlan, bizPlanId);
        DefaultHubPlanMapRow map = defaultHubPlanMapDao.findByBizPlanId(bizPlanId);
        if (map == null) {
            String hubPlanId = createHubPlan(bizPlan, hubProjectId, hubModuleId, operator);
            copyDocument(bizPlanId, hubPlanId, hubProjectId, operator);
            defaultHubPlanMapDao.insert(bizProjectId, bizPlanId, hubPlanId, contentHash);
        } else if (!StringUtils.equals(contentHash, map.getContentHash())) {
            updateHubPlan(map.getHubPlanId(), bizPlan, hubModuleId, operator);
            copyDocument(bizPlanId, map.getHubPlanId(), hubProjectId, operator);
            defaultHubPlanMapDao.updateHash(bizPlanId, contentHash);
        }
    }

    public void syncPlanDelete(String bizPlanId) {
        if (!defaultHubConfigService.isSyncEnabled()) {
            return;
        }
        DefaultHubPlanMapRow map = defaultHubPlanMapDao.findByBizPlanId(bizPlanId);
        if (map == null) {
            return;
        }
        testPlanMapper.deleteByPrimaryKey(map.getHubPlanId());
        jdbcTemplate.update("DELETE FROM test_plan_document WHERE test_plan_id = ?", map.getHubPlanId());
        defaultHubPlanMapDao.deleteByBizPlanId(bizPlanId);
    }

    public void reconcileProjectPlans(String bizProjectId, String operator) {
        List<String> planIds = jdbcTemplate.queryForList(
                "SELECT id FROM test_plan WHERE project_id = ? AND type = ? " +
                        "AND (imported_from_hub_plan_id IS NULL OR imported_from_hub_plan_id = '')",
                String.class, bizProjectId, TestPlanConstants.TEST_PLAN_TYPE_PLAN);
        for (String planId : planIds) {
            try {
                syncPlanUpsert(bizProjectId, planId, operator);
            } catch (Exception e) {
                LogUtils.error("hub plan reconcile failed: " + planId, e);
            }
        }
        for (DefaultHubPlanMapRow map : defaultHubPlanMapDao.listByBizProjectId(bizProjectId)) {
            TestPlan biz = testPlanMapper.selectByPrimaryKey(map.getBizPlanId());
            if (biz == null) {
                syncPlanDelete(map.getBizPlanId());
            }
        }
    }

    private boolean isImportedFromHub(String planId) {
        try {
            String imported = jdbcTemplate.queryForObject(
                    "SELECT imported_from_hub_plan_id FROM test_plan WHERE id = ?", String.class, planId);
            return StringUtils.isNotBlank(imported);
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveDefaultPlanModule(String hubProjectId) {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT id FROM test_plan_module WHERE project_id = ? AND parent_id = ? LIMIT 1",
                String.class, hubProjectId, ModuleConstants.ROOT_NODE_PARENT_ID);
        if (!ids.isEmpty()) {
            return ids.getFirst();
        }
        long now = System.currentTimeMillis();
        String id = IDGenerator.nextStr();
        jdbcTemplate.update(
                "INSERT INTO test_plan_module (id, project_id, name, parent_id, pos, create_time, update_time, create_user, update_user) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                id, hubProjectId, "默认模块", ModuleConstants.ROOT_NODE_PARENT_ID, 64L, now, now, "system", "system");
        return id;
    }

    private String createHubPlan(TestPlan src, String hubProjectId, String hubModuleId, String operator) {
        String hubPlanId = IDGenerator.nextStr();
        TestPlan hub = copyPlanHeader(src, hubPlanId, hubProjectId, hubModuleId, operator);
        hub.setNum(NumGenerator.nextNum(hubProjectId, ApplicationNumScope.TEST_PLAN));
        hub.setPos(testPlanService.getNextOrder(hubProjectId, TestPlanConstants.TEST_PLAN_DEFAULT_GROUP_ID));
        testPlanMapper.insert(hub);
        insertPlanConfig(hubPlanId, src.getId());
        return hubPlanId;
    }

    private void insertPlanConfig(String newPlanId, String sourcePlanId) {
        TestPlanConfig source = testPlanConfigMapper.selectByPrimaryKey(sourcePlanId);
        TestPlanConfig config = new TestPlanConfig();
        config.setTestPlanId(newPlanId);
        if (source != null) {
            config.setAutomaticStatusUpdate(Boolean.TRUE.equals(source.getAutomaticStatusUpdate()));
            config.setRepeatCase(Boolean.TRUE.equals(source.getRepeatCase()));
            config.setPassThreshold(source.getPassThreshold() != null ? source.getPassThreshold() : 100.0);
            config.setCaseRunMode(StringUtils.defaultIfBlank(source.getCaseRunMode(), "PARALLEL"));
        } else {
            config.setAutomaticStatusUpdate(false);
            config.setRepeatCase(false);
            config.setPassThreshold(100.0);
            config.setCaseRunMode("PARALLEL");
        }
        testPlanConfigMapper.insertSelective(config);
    }

    private void updateHubPlan(String hubPlanId, TestPlan src, String hubModuleId, String operator) {
        TestPlan hub = copyPlanHeader(src, hubPlanId, defaultHubProjectService.getDefaultProjectId(), hubModuleId, operator);
        testPlanMapper.updateByPrimaryKeySelective(hub);
    }

    private TestPlan copyPlanHeader(TestPlan src, String id, String projectId, String moduleId, String operator) {
        TestPlan p = new TestPlan();
        p.setId(id);
        p.setProjectId(projectId);
        p.setModuleId(moduleId);
        p.setGroupId(TestPlanConstants.TEST_PLAN_DEFAULT_GROUP_ID);
        p.setName(src.getName());
        p.setStatus(TestPlanConstants.TEST_PLAN_STATUS_NOT_ARCHIVED);
        p.setType(TestPlanConstants.TEST_PLAN_TYPE_PLAN);
        p.setTags(src.getTags());
        p.setPlannedStartTime(src.getPlannedStartTime());
        p.setPlannedEndTime(src.getPlannedEndTime());
        p.setDescription(src.getDescription());
        p.setCreateUser(operator);
        p.setUpdateUser(operator);
        long now = System.currentTimeMillis();
        p.setCreateTime(now);
        p.setUpdateTime(now);
        return p;
    }

    private void copyDocument(String bizPlanId, String hubPlanId, String hubProjectId, String operator) {
        TestPlanDocument doc = testPlanDocumentMapper.selectByTestPlanId(bizPlanId);
        if (doc == null) {
            return;
        }
        TestPlanDocument existing = testPlanDocumentMapper.selectByTestPlanId(hubPlanId);
        long now = System.currentTimeMillis();
        // TODO: 图片文件拷贝到枢纽项目并重写 content 引用
        if (existing == null) {
            TestPlanDocument copy = new TestPlanDocument();
            copy.setId(IDGenerator.nextStr());
            copy.setTestPlanId(hubPlanId);
            copy.setProjectId(hubProjectId);
            copy.setContent(StringUtils.defaultString(doc.getContent()));
            copy.setContentType(doc.getContentType());
            copy.setCreateTime(now);
            copy.setUpdateTime(now);
            copy.setCreateUser(operator);
            copy.setUpdateUser(operator);
            testPlanDocumentMapper.insert(copy);
        } else {
            existing.setContent(StringUtils.defaultString(doc.getContent()));
            existing.setContentType(doc.getContentType());
            existing.setUpdateTime(now);
            existing.setUpdateUser(operator);
            testPlanDocumentMapper.updateByPrimaryKeySelective(existing);
        }
    }

    private String computeHash(TestPlan plan, String planId) {
        TestPlanDocument doc = testPlanDocumentMapper.selectByTestPlanId(planId);
        StringBuilder sb = new StringBuilder();
        sb.append(plan.getName()).append('|').append(plan.getDescription())
                .append('|').append(plan.getPlannedStartTime()).append('|').append(plan.getPlannedEndTime());
        if (doc != null) {
            sb.append('|').append(StringUtils.defaultString(doc.getContent()));
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return sb.toString();
        }
    }
}
