package io.metersphere.system.dto.wecom;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WecomUserDTO {
    private String userid;
    private String name;
    private String mobile;
    private String email;
    @JsonProperty("biz_mail")
    private String bizMail;
    private String position;
    private List<Long> department;
    @JsonProperty("main_department")
    private Long mainDepartment;
    private Integer status;
}
