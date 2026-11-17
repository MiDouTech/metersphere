package io.metersphere.functional.planreview;

import java.util.Map;

/** Native modules resume their own stored operation inside the caller's transaction. */
public interface PlanReviewOperationResumer {
    boolean supports(String sourceKind,String operationType);
    void resume(Map<String,Object> operation,String executingActor);
    default void onTakeover(Map<String,Object> operation,String newActor,String approvedBy) { }
    default void onTerminate(Map<String,Object> operation,String actor) { }
}
