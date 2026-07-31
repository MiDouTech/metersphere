package io.metersphere.functional.dto;

import io.metersphere.functional.domain.FunctionalCase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @author wx
 */
@Data
public class FunctionalCasePageDTO extends FunctionalCase {

    @Schema(description = "自定义字段集合")
    private List<FunctionalCaseCustomFieldDTO> customFields;

    @Schema(description = "版本名称")
    private String versionName;

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

}
