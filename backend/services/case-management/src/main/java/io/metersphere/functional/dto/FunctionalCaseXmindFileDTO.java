package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FunctionalCaseXmindFileDTO {
    @Schema(description = "主键")
    private String id;
    @Schema(description = "项目ID")
    private String projectId;
    @Schema(description = "显示名称")
    private String name;
    @Schema(description = "原始文件名")
    private String originalName;
    @Schema(description = "大小(字节)")
    private Long size;
    @Schema(description = "创建时间")
    private Long createTime;
    @Schema(description = "更新时间")
    private Long updateTime;
    @Schema(description = "上传人")
    private String createUser;
    @Schema(description = "更新人")
    private String updateUser;
}
