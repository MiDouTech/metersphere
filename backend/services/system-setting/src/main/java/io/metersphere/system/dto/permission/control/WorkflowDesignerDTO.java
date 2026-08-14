package io.metersphere.system.dto.permission.control;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkflowDesignerDTO {
    private List<Status> statuses = new ArrayList<>();
    private List<Transition> transitions = new ArrayList<>();

    @Data
    public static class Status {
        private String id;
        private String code;
        private String name;
        private String remark;
        private Boolean initial;
        private Boolean terminal;
        private Boolean enabled;
        private Integer pos;
    }

    @Data
    public static class Transition {
        private String id;
        private String fromId;
        private String toId;
        private Boolean enabled;
    }
}
