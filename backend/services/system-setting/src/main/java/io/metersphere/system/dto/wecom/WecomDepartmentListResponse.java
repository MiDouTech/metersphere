package io.metersphere.system.dto.wecom;

import lombok.Data;

import java.util.List;

@Data
public class WecomDepartmentListResponse {
    private Integer errcode;
    private String errmsg;
    private List<WecomDepartmentDTO> department;
}
