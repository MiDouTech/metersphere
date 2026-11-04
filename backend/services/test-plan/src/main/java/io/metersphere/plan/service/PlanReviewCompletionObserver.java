package io.metersphere.plan.service;

import io.metersphere.functional.planreview.PlanReviewLifecycleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PlanReviewCompletionObserver {
    private final JdbcTemplate jdbc; private final PlanReviewLifecycleService lifecycle; private final TransactionTemplate tx;
    private String cursor="";
    public PlanReviewCompletionObserver(JdbcTemplate jdbc,PlanReviewLifecycleService lifecycle,PlatformTransactionManager manager){this.jdbc=jdbc;this.lifecycle=lifecycle;this.tx=new TransactionTemplate(manager);tx.setTimeout(30);}
    @Scheduled(fixedDelay=1000,initialDelay=30000)
    public void drain(){
        var ids=jdbc.queryForList("SELECT p.id FROM test_plan p WHERE p.type='TEST_PLAN' AND p.status='NOT_ARCHIVED' AND p.id>? AND (EXISTS(SELECT 1 FROM plan_review_relation r WHERE r.active_plan_id=p.id AND r.state='ACTIVE' AND r.sync_state='SYNCED') OR EXISTS(SELECT 1 FROM plan_review_lifecycle_preview l WHERE l.object_side='plan' AND l.object_id=p.id AND l.action='RESTORE_EDIT' AND l.consumed_at IS NOT NULL)) ORDER BY p.id LIMIT 20",String.class,cursor);
        if(ids.isEmpty()){cursor="";return;}
        for(String id:ids){cursor=id;try{tx.executeWithoutResult(status->lifecycle.observePlanCompletion(id));}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Plan completion snapshot pending; source results preserved");}}
    }
}
