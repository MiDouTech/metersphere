package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import java.util.Map;

/** Only contract-owned field paths and hints may be placed in details; never input values. */
public final class AgentFieldValidationException extends MSException {
    private final String code;
    private final Map<String, Object> details;

    public AgentFieldValidationException(String code, Map<String, Object> details) {
        super(code);
        this.code = code;
        this.details = Map.copyOf(details);
    }

    public String code() { return code; }
    public Map<String, Object> details() { return details; }
}
