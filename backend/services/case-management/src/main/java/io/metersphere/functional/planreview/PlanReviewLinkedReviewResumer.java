package io.metersphere.functional.planreview;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

/** Resuming an uncreated review returns a fresh preview; it never bypasses confirmation. */
@Component
public class PlanReviewLinkedReviewResumer implements PlanReviewOperationResumer {
    private final PlanReviewApplicationService service;
    private final JdbcTemplate jdbc;
    public PlanReviewLinkedReviewResumer(PlanReviewApplicationService service,JdbcTemplate jdbc){this.service=service;this.jdbc=jdbc;}
    public boolean supports(String source,String type){return java.util.Set.of("WEB","AGENT").contains(source)&&"CREATE_REVIEW".equals(type);}
    public void resume(Map<String,Object> operation,String actor){
        if(operation.get("relation_id")!=null)return; // Member recovery remains a separate M08 confirmation.
        var preview=service.recoverNewReview((String)operation.get("project_id"),(String)operation.get("id"),actor);
        jdbc.update("UPDATE plan_review_operation SET result_json=? WHERE id=? AND project_id=?",io.metersphere.sdk.util.JSON.toJSONString(Map.of("newReviewPreview",preview)),operation.get("id"),operation.get("project_id"));
    }
}
