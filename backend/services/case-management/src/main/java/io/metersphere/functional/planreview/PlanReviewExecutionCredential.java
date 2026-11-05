package io.metersphere.functional.planreview;

/** Opaque credential identity only. Never returns a token or secret value. */
public interface PlanReviewExecutionCredential {
    String currentReference();
    boolean validate(String reference,String project,String actor);
    default boolean validateImport(String reference,String project,String actor){return false;}
    default boolean validateLinkedReview(String reference,String project,String actor){return validate(reference,project,actor);}
}
