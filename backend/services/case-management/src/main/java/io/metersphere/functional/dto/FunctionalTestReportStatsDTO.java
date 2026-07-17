package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FunctionalTestReportStatsDTO {

    @Schema(description = "执行统计")
    private ExecStats exec = new ExecStats();

    @Schema(description = "缺陷-处理人×处理状态（MVP 可为空数组）")
    private List<Object> bugHandlerStatus = new ArrayList<>();

    @Schema(description = "缺陷-类型分布（MVP 可为空数组）")
    private List<Object> bugType = new ArrayList<>();

    @Schema(description = "遗留风险用例（失败/阻塞）")
    private List<FunctionalTestReportRiskCaseDTO> riskCases = new ArrayList<>();

    @Schema(description = "通过率公式说明")
    private String passRateFormulaNote;

    @Schema(description = "缺陷类型字段解析说明（找不到字段时提示）")
    private String bugTypeMessage;

    @Data
    public static class ExecStats {
        private long total;
        private long pass;
        private long fail;
        private long block;
        private String execRate = "-";
        private String passRate = "-";
    }
}
