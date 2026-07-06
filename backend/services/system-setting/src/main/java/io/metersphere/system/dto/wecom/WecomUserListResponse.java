package io.metersphere.system.dto.wecom;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WecomUserListResponse {
    private Integer errcode;
    private String errmsg;
    @JsonProperty("userlist")
    private List<WecomUserDTO> userList;
}
