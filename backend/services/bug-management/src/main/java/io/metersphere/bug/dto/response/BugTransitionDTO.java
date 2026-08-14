package io.metersphere.bug.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BugTransitionDTO {
    private String bugId;
    private String workflowId;
    private Integer workflowVersion;
    private Status currentStatus;
    private Long updateTime;
    private String unavailableReason;
    private List<Transition> transitions = new ArrayList<>();

    @Data
    public static class Status {
        private String id;
        private String name;
    }

    @Data
    public static class Transition {
        private String transitionId;
        private Status targetStatus;
        private Boolean visible;
        private Boolean operable;
        private Boolean overrideRequired;
        private List<String> matchedRoles = new ArrayList<>();
        private String disabledReason;
    }
}
