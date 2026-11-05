package io.metersphere.functional.service;

import io.metersphere.functional.planreview.PlanReviewReviewWriter;
import io.metersphere.functional.request.BaseAssociateCaseRequest;
import io.metersphere.functional.request.CaseReviewRequest;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class PlanReviewReviewWriterAdapter implements PlanReviewReviewWriter {
    @Resource @Lazy private CaseReviewService reviews;
    @Override public void create(Map<String,Object> draft,String reservedReviewId,String actor) {
        var request=JSON.parseObject(JSON.toJSONString(draft),CaseReviewRequest.class);
        request.setId(null);
        var selection=new BaseAssociateCaseRequest();
        selection.setProjectId(request.getProjectId()); selection.setSelectIds(List.of()); selection.setReviewers(request.getReviewers());
        request.setBaseAssociateCaseRequest(selection);
        reviews.addReservedCaseReview(request,actor,reservedReviewId);
    }
}
