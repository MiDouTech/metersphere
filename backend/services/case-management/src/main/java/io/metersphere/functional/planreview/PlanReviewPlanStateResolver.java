package io.metersphere.functional.planreview;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** NOT_ARCHIVED is a storage state, not an editable-business-state assertion. */
@Service
public class PlanReviewPlanStateResolver {
    private final List<PlanReviewPlanStateProvider> providers;

    public PlanReviewPlanStateResolver(List<PlanReviewPlanStateProvider> providers) { this.providers = List.copyOf(providers); }
    public Map<String,Object> statisticsSnapshot(String planId){return providers.size()==1?providers.getFirst().statisticsSnapshot(planId):Map.of("status","UNKNOWN");}
    public String executionRevision(String planId){return providers.size()==1?providers.getFirst().executionRevision(planId):"UNKNOWN";}
    public String completionState(String planId){return providers.size()==1?providers.getFirst().completionState(planId):"UNKNOWN";}

    public Map<String, Object> resolve(Map<String, Object> plan) {
        if (!"NOT_ARCHIVED".equals(plan.get("status"))) return plan;
        String computed = providers.size() == 1 ? providers.getFirst().businessState(Objects.toString(plan.get("id"))) : "UNKNOWN";
        var result = new java.util.LinkedHashMap<>(plan);
        result.put("storageStatus", plan.get("status"));
        result.put("status", PlanReviewPolicy.State.parse(computed).name());
        return result;
    }
}
