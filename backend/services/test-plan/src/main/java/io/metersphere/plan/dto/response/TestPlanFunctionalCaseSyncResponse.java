package io.metersphere.plan.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "测试用例同步结果")
public class TestPlanFunctionalCaseSyncResponse {

    @Schema(description = "已同步的测试计划数量")
    private int planCount;

    @Schema(description = "跳过的测试计划数量")
    private int skippedPlanCount;

    @Schema(description = "新增的计划用例数量")
    private int addedCount;

    @Schema(description = "更新执行状态的计划用例数量")
    private int updatedCount;

    @Schema(description = "删除的计划用例数量")
    private int removedCount;

    public void add(TestPlanFunctionalCaseSyncResponse response) {
        this.planCount += response.planCount;
        this.skippedPlanCount += response.skippedPlanCount;
        this.addedCount += response.addedCount;
        this.updatedCount += response.updatedCount;
        this.removedCount += response.removedCount;
    }
}
