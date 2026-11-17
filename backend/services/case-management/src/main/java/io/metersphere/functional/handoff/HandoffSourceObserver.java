package io.metersphere.functional.handoff;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import java.util.*;

/** Uses the existing write mutex: catches WEB/API/import/background writers without coupling their responses to target I/O. */
@Aspect
@Component
public class HandoffSourceObserver {
    private final JdbcTemplate jdbc;
    private final HandoffService service;
    public HandoffSourceObserver(JdbcTemplate jdbc,HandoffService service){this.jdbc=jdbc;this.service=service;}
    @AfterReturning(value="execution(* io.metersphere.functional.planreview.PlanReviewPersistence.lockSource(String)) && args(plan)",argNames="plan")
    public void plan(String plan){register(plan);}
    @AfterReturning(value="execution(* io.metersphere.functional.planreview.PlanReviewPersistence.lockReview(String)) && args(review)",argNames="review")
    public void review(String review){for(String plan:jdbc.queryForList("SELECT plan_id FROM plan_review_relation WHERE review_id=?",String.class,review))register(plan);}
    private void register(String plan){
        if(!TransactionSynchronizationManager.isSynchronizationActive()||TransactionSynchronizationManager.isCurrentTransactionReadOnly())return;
        Batch batch=TransactionSynchronizationManager.getSynchronizations().stream().filter(Batch.class::isInstance).map(Batch.class::cast).findFirst().orElse(null);
        if(batch==null){batch=new Batch();TransactionSynchronizationManager.registerSynchronization(batch);}
        if(!batch.running){var projects=jdbc.queryForList("SELECT project_id FROM test_plan WHERE id=?",String.class,plan);if(projects.size()==1)batch.plans.put(plan,projects.getFirst());}
    }
    private final class Batch implements TransactionSynchronization {
        private final SortedMap<String,String> plans=new TreeMap<>();private boolean running;
        @Override public void beforeCommit(boolean readOnly){
            if(readOnly)return;running=true;
            for(String plan:plans.keySet()){
                var projects=jdbc.queryForList("SELECT project_id FROM test_plan WHERE id=?",String.class,plan);
                try { jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                    var savepoint=connection.setSavepoint();
                    try {
                        if(projects.size()==1)service.captureCore(projects.getFirst(),plan,true);
                        else service.sourceDeleted(plans.get(plan),plan);
                        connection.releaseSavepoint(savepoint);
                    } catch(RuntimeException unavailable) {
                        connection.rollback(savepoint);
                        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Webhook source capture deferred; category=LOCAL_CAPTURE_UNAVAILABLE");
                    }
                    return null;
                }); } catch(RuntimeException unavailable) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Webhook capture deferred; category=SAVEPOINT_UNAVAILABLE");
                }
            }
        }
    }
}
