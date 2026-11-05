package io.metersphere.functional.planreview;

/** Implemented by test-plan; avoids a reverse Maven dependency from case-management. */
public interface PlanReviewPlanStateProvider {
    String businessState(String planId);
    default String executionRevision(String planId){return "UNKNOWN";}
    default String completionState(String planId){return businessState(planId);}
    default java.util.Map<String,Object> statisticsSnapshot(String planId){return java.util.Map.of("status",businessState(planId));}
}
