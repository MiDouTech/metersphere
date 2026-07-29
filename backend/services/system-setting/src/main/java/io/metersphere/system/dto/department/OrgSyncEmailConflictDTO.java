package io.metersphere.system.dto.department;

import lombok.Data;

@Data
public class OrgSyncEmailConflictDTO {
    private String id;
    private String organizationId;
    private String wecomUserid;
    private String pendingUserId;
    private String wecomUserName;
    private String conflictEmail;
    private String occupiedUserId;
    private String occupiedUserName;
    private String conflictScene;
    private String status;
    private Long createTime;
}
