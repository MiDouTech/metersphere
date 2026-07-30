package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class AgentToken implements Serializable {
    private String id;
    private String name;
    private String tokenPrefix;
    private String tokenHash;
    private String userId;
    private String projectId;
    /** JSON 数组字符串，空/null 表示全部项目 */
    private String projectIds;
    private String scopes;
    private Long expireTime;
    private Boolean enable;
    private Long createTime;
    private String createUser;
}
