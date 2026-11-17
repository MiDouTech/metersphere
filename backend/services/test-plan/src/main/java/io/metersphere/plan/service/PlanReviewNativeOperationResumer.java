package io.metersphere.plan.service;

import io.metersphere.functional.planreview.PlanReviewOperationResumer;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class PlanReviewNativeOperationResumer implements PlanReviewOperationResumer {
    @jakarta.annotation.Resource @org.springframework.context.annotation.Lazy private TestPlanService plans;
    @Override public boolean supports(String source,String type){return "PLAN_SERVICE".equals(source)&&java.util.Set.of("CREATE_PAIR","COPY_PAIR","COPY_GROUP").contains(type);}
    @Override public void resume(Map<String,Object> operation,String actor){plans.resumeReservedCreation(operation,actor);}
}
