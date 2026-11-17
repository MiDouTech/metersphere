package io.metersphere.plan.hub.service;
import io.metersphere.functional.planreview.PlanReviewOperationResumer;
import org.springframework.stereotype.Component;
import java.util.Map;
@Component
public class PlanReviewHubOperationResumer implements PlanReviewOperationResumer {
    @jakarta.annotation.Resource private DefaultHubPlanImportService imports;
    public boolean supports(String source,String type){return "HUB_IMPORT".equals(source)&&"CREATE_PAIR".equals(type);}
    public void resume(Map<String,Object> operation,String actor){imports.resumeReserved(operation,actor);}
    public void onTakeover(Map<String,Object> operation,String actor,String approvedBy){imports.handoffReserved(operation);}
    public void onTerminate(Map<String,Object> operation,String actor){imports.terminateReserved(operation);}
}
