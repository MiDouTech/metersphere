package io.metersphere.functional.planreview;

import java.util.Map;

/** Existing review creation remains responsible for native fields and reviewer relations. */
public interface PlanReviewReviewWriter {
    void create(Map<String,Object> draft, String reservedReviewId, String actor);
}
