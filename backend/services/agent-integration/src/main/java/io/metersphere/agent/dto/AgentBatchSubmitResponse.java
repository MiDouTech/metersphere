package io.metersphere.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentBatchSubmitResponse {
    private int total;
    private int success;
    private int failed;
    private List<AgentBatchSubmitError> errors = new ArrayList<>();

    @Data
    public static class AgentBatchSubmitError {
        private String caseId;
        private String message;

        public AgentBatchSubmitError(String caseId, String message) {
            this.caseId = caseId;
            this.message = message;
        }
    }
}
