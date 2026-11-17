package io.metersphere.agent.service;

import io.metersphere.functional.planreview.PlanReviewOperationResumer;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class ImportFollowupOperationResumer implements PlanReviewOperationResumer {
    @jakarta.annotation.Resource private ImportFollowupCoordinator followups;
    @Override public boolean supports(String source,String type){return "IMPORT_FOLLOWUP".equals(source)&&java.util.Set.of("CREATE_PAIR","APPEND").contains(type);}
    @Override public void resume(Map<String,Object> operation,String actor){followups.resumeOperation(operation,actor);}
    @Override public void onTakeover(Map<String,Object> operation,String actor,String approver){followups.takeoverOperation(operation,actor,approver);}
    @Override public void onTerminate(Map<String,Object> operation,String actor){followups.terminateOperation(operation,actor);}
}
