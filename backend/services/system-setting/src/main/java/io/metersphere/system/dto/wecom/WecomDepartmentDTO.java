package io.metersphere.system.dto.wecom;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WecomDepartmentDTO {
    private Long id;
    private String name;
    private Long parentid;
    private Long order;
    @JsonProperty("department_leader")
    private List<String> departmentLeader;
}
