package io.metersphere.functional.dto;

import lombok.Data;

@Data
public class FunctionalTestReportBugCountDTO {
    private String handleUser;
    private String handleUserName;
    private String status;
    private String statusName;
    private String type;
    private Long count;
}
