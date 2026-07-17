package io.metersphere.plan.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class TestPlanDocument implements Serializable {
    private String id;
    private String testPlanId;
    private String projectId;
    private String content;
    private String contentType;
    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;
}
