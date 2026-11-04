package io.metersphere.functional.service;
import io.metersphere.functional.planreview.PlanReviewCurrentActor;
import io.metersphere.system.utils.SessionUtils;
import org.springframework.stereotype.Service;
@Service
public class PlanReviewCurrentActorAdapter implements PlanReviewCurrentActor {
    public String current(){return SessionUtils.getUserId();}
}
