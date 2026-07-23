package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentCaseBatchCreateResponse {
    @Schema(description = "成功创建的用例")
    private List<CreatedCase> created = new ArrayList<>();

    @Schema(description = "失败项")
    private List<FailedCase> errors = new ArrayList<>();

    @Data
    public static class CreatedCase {
        private String caseId;
        private Long num;
        private String name;
        private String moduleId;
    }

    @Data
    public static class FailedCase {
        private String name;
        private String error;
    }
}
