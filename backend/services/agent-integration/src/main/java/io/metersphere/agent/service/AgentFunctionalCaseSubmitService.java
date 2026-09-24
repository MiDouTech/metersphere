package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import io.metersphere.sdk.exception.MSException;
import org.springframework.stereotype.Service;

/** Compatibility boundary: direct legacy result writes cannot bypass server-bound execution gates. */
@Service
public class AgentFunctionalCaseSubmitService {
    public void submit(AgentCaseSubmitRequest request) {
        throw new MSException("QUALITY_LEGACY_SUBMIT_FORBIDDEN");
    }
}
