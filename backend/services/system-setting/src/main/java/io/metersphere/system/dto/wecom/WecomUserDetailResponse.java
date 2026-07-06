package io.metersphere.system.dto.wecom;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WecomUserDetailResponse extends WecomUserDTO {
    private Integer errcode;
    private String errmsg;
}
