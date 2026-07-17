package io.metersphere.functional.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class FunctionalCaseXmindFile implements Serializable {
    private String id;
    private String projectId;
    private String name;
    private String originalName;
    private String fileId;
    private Long size;
    private String storage;
    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;
}
