package io.metersphere.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentTokenListItemDTO {
    private String id;
    private String name;
    private String userId;
    /** 兼容：首个项目或旧字段 */
    private String projectId;
    /** 可访问项目；空列表表示全部项目 */
    private List<String> projectIds;
    /** 展示：全部项目 / 数量 */
    private String projectScopeLabel;
    private String scopes;
    private Long expireTime;
    private Boolean enable;
    private Long createTime;
    private String createUser;
}
