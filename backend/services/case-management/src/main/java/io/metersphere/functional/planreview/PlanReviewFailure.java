package io.metersphere.functional.planreview;

/** Safe API error; messages never contain database details, tokens or opinion text. */
public final class PlanReviewFailure extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public PlanReviewFailure(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
}
