package io.metersphere.functional.planreview;

import java.util.Map;
import java.util.Set;

/** No subject/object details are copied into the inbox. Detail reads reauthorize separately. */
public interface PlanReviewNoticeChannel {
    Set<String> recipients(Map<String,Object> relation,String reason);
    Set<String> responsibles(String project,String reason);
    /** Returns CURRENT or a safe reason. Must check account and project membership on every delivery. */
    String eligibility(String project,String recipient);
    /** Called inside the notice transaction; insert and receipt commit atomically. */
    long publish(String recipient,String noticeId,long detectedAt,boolean recovered,boolean ended,String type);
}
