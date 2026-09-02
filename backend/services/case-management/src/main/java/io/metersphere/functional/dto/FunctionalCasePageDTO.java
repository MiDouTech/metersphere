package io.metersphere.functional.dto;

import io.metersphere.functional.domain.FunctionalCase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author wx
 */
@Data
public class FunctionalCasePageDTO extends FunctionalCase {

    @Schema(description = "自定义字段集合")
    private List<FunctionalCaseCustomFieldDTO> customFields;

    @Schema(description = "版本名称")
    private String versionName;

    @Schema(description = "所属模块名称")
    private String moduleName;

    @Schema(description = "创建人名称")
    private String createUserName;

    @Schema(description = "更新人名称")
    private String updateUserName;

    @Schema(description = "删除人名称")
    private String deleteUserName;

    @Schema(description = "执行人姓名")
    private String executeUserName;

    @Schema(description = "最后执行人姓名")
    private String lastExecuteUserName;

    @Schema(description = "关联测试计划进度概览")
    private List<FunctionalCaseTestPlanOverviewDTO> testPlans;

    @Schema(description = "当前用户个人执行进度")
    private FunctionalCasePersonalProgressDTO personalProgress;

    @Schema(description = "已引用项目数（只统计已进入测试计划的项目副本）")
    private Integer referencedProjectCount;

    @Schema(description = "已引用项目摘要")
    private List<Map<String, Object>> referencedProjects;

    @Schema(description = "测试资产建立方式")
    private String creationSource;

    @Schema(description = "测试资产业务分类 ID")
    private String assetCategoryId;

    @Schema(description = "测试资产业务分类名称")
    private String assetCategoryName;

    @Schema(description = "测试资产业务分类完整路径")
    private String assetCategoryPath;

}
