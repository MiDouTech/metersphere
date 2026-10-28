package io.metersphere.functional.service;

import io.metersphere.functional.hub.service.DefaultHubCaseSyncService;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.notice.constants.NoticeConstants;
import io.metersphere.system.service.CommonNoticeSendService;
import io.metersphere.system.uid.IDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/** At-least-once, bounded post-import effects, persisted atomically with the imported cases. */
@Service
public class FunctionalImportEffectOutbox {
    private static final Logger LOG = LoggerFactory.getLogger(FunctionalImportEffectOutbox.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DefaultHubCaseSyncService hub;
    private final FunctionalCaseService cases;
    private final UserMapper users;
    private final CommonNoticeSendService notices;
    private volatile long nextScan;
    private final java.util.concurrent.atomic.LongAdder delivered = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder failures = new java.util.concurrent.atomic.LongAdder();
    public long deliveredCount() { return delivered.sum(); }
    public long failureCount() { return failures.sum(); }

    public FunctionalImportEffectOutbox(JdbcTemplate jdbc, PlatformTransactionManager manager,
            @Lazy DefaultHubCaseSyncService hub, @Lazy FunctionalCaseService cases,
            UserMapper users, CommonNoticeSendService notices) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setTimeout(25);
        this.hub = hub;
        this.cases = cases;
        this.users = users;
        this.notices = notices;
    }

    @jakarta.annotation.Resource private FunctionalReviewRecountService reviewRecount;
    @Lazy @jakarta.annotation.Resource private io.metersphere.functional.asset.service.CaseAssetService assets;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueue(String projectId, String userId, List<String> ids, List<Map> resources, String event) {
        reviewRecount.enqueueForCases(ids);
        long now = System.currentTimeMillis();
        for (String id : ids) insert("CASE_SYNC", projectId, userId, Map.of("caseId", id), now);
        for (Map resource : resources) insert("NOTICE", projectId, userId, Map.of("resource", resource, "event", event), now);
    }

    private void insert(String type, String project, String user, Object payload, long now) {
        jdbc.update("INSERT INTO functional_import_effect_outbox(id,effect_type,project_id,user_id,payload,next_retry_at,create_time,update_time) VALUES (?,?,?,?,?,?,?,?)",
                java.util.UUID.randomUUID().toString(), type, project, user, JSON.toJSONString(payload), now, now, now);
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 45000, scheduler = "functionalImportEffectsScheduler")
    @Transactional
    public void reconcileStaleProjection() {
        // Only the migration-marked backlog; normal writes enqueue in their source transaction.
        var rows=jdbc.queryForList("SELECT f.id,f.project_id,f.update_user FROM case_asset_source_relation r JOIN functional_case f ON BINARY f.project_id=BINARY r.source_project_id AND BINARY COALESCE(NULLIF(f.ref_id,''),f.id)=BINARY r.source_case_id WHERE r.source_update_time=0 AND f.deleted=0 AND f.latest=1 AND NOT EXISTS (SELECT 1 FROM functional_import_effect_outbox e WHERE BINARY e.project_id=BINARY f.project_id AND e.effect_type='CASE_SYNC' AND e.state IN ('PENDING','FAILED') AND BINARY JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.caseId'))=BINARY f.id) ORDER BY r.id LIMIT 10 FOR UPDATE SKIP LOCKED");
        long now=System.currentTimeMillis();
        for(var row:rows)insert("CASE_SYNC",(String)row.get("project_id"),(String)row.get("update_user"),Map.of("caseId",row.get("id")),now);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 30000, scheduler = "functionalImportEffectsScheduler")
    public void drain() {
        if (System.currentTimeMillis() < nextScan) return;
        try {
            for (int i = 0; i < 5; i++) {
                String[] selected = new String[1];
                try {
                    Boolean processed = transaction.execute(status -> {
                        var rows = jdbc.queryForList("SELECT id,effect_type,project_id,user_id,payload FROM functional_import_effect_outbox WHERE state='PENDING' AND next_retry_at<=? ORDER BY next_retry_at,id LIMIT 1 FOR UPDATE SKIP LOCKED", System.currentTimeMillis());
                        if (rows.isEmpty()) return false;
                        var row = rows.getFirst();
                        selected[0] = (String) row.get("id");
                        Map payload = JSON.parseObject((String) row.get("payload"), Map.class);
                        String project = (String) row.get("project_id"), user = (String) row.get("user_id");
                        if ("CASE_SYNC".equals(row.get("effect_type"))) {
                            String caseId = (String) payload.get("caseId");
                            assets.upsertForProject(project);
                            hub.syncCaseUpsert(project, caseId, user);
                            cases.publishImportedCaseAsset(caseId, user);
                        } else {
                            var operator = users.selectByPrimaryKey(user);
                            if (operator == null) throw new IllegalStateException("Import operator no longer exists");
                            java.util.Locale previous = org.springframework.context.i18n.LocaleContextHolder.getLocale();
                            try {
                                notices.sendNoticeNow(NoticeConstants.TaskType.FUNCTIONAL_CASE_TASK, (String) payload.get("event"),
                                        List.of((Map) payload.get("resource")), operator, project);
                            } finally {
                                org.springframework.context.i18n.LocaleContextHolder.setLocale(previous);
                            }
                        }
                        jdbc.update("UPDATE functional_import_effect_outbox SET state='SUCCESS',update_time=?,error_code=NULL,error_message=NULL WHERE id=?", System.currentTimeMillis(), selected[0]);
                        return true;
                    });
                    if (!Boolean.TRUE.equals(processed)) break;
                    delivered.increment();
                } catch (Exception failure) {
                    failures.increment();
                    if (selected[0] == null) throw failure;
                    long now = System.currentTimeMillis();
                    jdbc.update("UPDATE functional_import_effect_outbox SET next_retry_at=?+LEAST(900000,30000*POW(2,LEAST(attempts,5))),attempts=attempts+1,state=IF(attempts>=8,'FAILED','PENDING'),error_code='IMPORT_EFFECT_FAILED',error_message=?,update_time=? WHERE id=? AND state='PENDING'", now, safeSyncFailure(failure,selected[0]), now, selected[0]);
                    LOG.warn("Import effect failed id={}; retry recorded", selected[0], failure);
                }
            }
        } catch (Exception unavailable) {
            nextScan = System.currentTimeMillis() + 30000;
            LOG.warn("Import effect store unavailable; pausing scan for 30 seconds", unavailable);
        }
    }

    private String safeSyncFailure(Exception failure,String reference) {
        String message=failure.getMessage();
        if(failure instanceof io.metersphere.sdk.exception.MSException && message!=null &&
                (message.startsWith("同一源用例存在重复资产映射") || message.startsWith("历史资产映射已失效") ||
                 message.startsWith("资产持续同步已关闭") || message.startsWith("资产存储项目未配置") ||
                 message.startsWith("项目资产目录尚未就绪") || message.startsWith("资产镜像已被彻底删除") ||
                 message.equals("同一项目存在多个资产目录映射，请核对来源后重试") ||
                 message.equals("该项目存在多个历史资产目录，请先核对目录来源后重试") ||
                 message.equals("项目资产目录名称冲突，请修改项目名称后重试")))
            return org.apache.commons.lang3.StringUtils.left(message,900)+"；排查编号 "+reference;
        return "资产同步未完成，请使用排查编号联系管理员："+reference;
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000, scheduler = "functionalImportEffectsScheduler")
    public void cleanupDelivered() {
        // Preserve failed/pending work for reconciliation; delete only old completed payloads, in bounded batches.
        jdbc.update("DELETE FROM functional_import_effect_outbox WHERE state='SUCCESS' AND update_time<? ORDER BY update_time,id LIMIT 1000",
                System.currentTimeMillis() - java.time.Duration.ofDays(7).toMillis());
    }
}
