package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentTokenCreateRequest {
    @NotBlank
    private String name;
    /** 关联用户（登录/执行身份）；空则后端用当前登录用户 */
    private String userId;
    /** 可访问项目；空或不传 = 全部项目 */
    private List<String> projectIds;
    /** @deprecated 兼容旧单项目字段，优先用 projectIds */
    private String projectId;
    @NotBlank
    private String scopes;
    private Long expireTime;
}
