package io.metersphere.agent.quality;

import io.metersphere.sdk.exception.MSException;
import java.util.List;

public class QualityPolicyValidationException extends MSException {
    private final List<QualityPolicyValidator.Issue> errors;
    public QualityPolicyValidationException(List<QualityPolicyValidator.Issue> errors) {
        super("QUALITY_POLICY_INVALID");
        this.errors = List.copyOf(errors);
    }
    public List<QualityPolicyValidator.Issue> getErrors() { return errors; }
}
