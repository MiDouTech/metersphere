package io.metersphere.functional.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class FunctionalTestReport implements Serializable {
    private String id;
    private String projectId;
    private String name;
    private String planId;
    private String content;
    private String statsSnapshot;
    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;
}
