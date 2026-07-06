package io.metersphere.system.dto.wecom;

import lombok.Data;

import java.util.List;

@Data
public class WecomUserDTO {
    private String userid;
    private String name;
    private String mobile;
    private String email;
    private String position;
    private List<Long> department;
    private Integer status;
}
