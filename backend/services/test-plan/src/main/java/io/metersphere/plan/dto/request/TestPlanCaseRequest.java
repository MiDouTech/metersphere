package io.metersphere.plan.dto.request;

import io.metersphere.system.dto.sdk.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @author wx
 */
@Data
public class TestPlanCaseRequest extends BasePageRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "测试计划id")
    @NotBlank(message = "{test_plan.id.not_blank}")
    private String testPlanId;

    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @Schema(description = "版本id")
    private String versionId;

    @Schema(description = "版本来源")
    private String refId;

    @Schema(description = "模块id")
    private List<String> moduleIds;

    @Schema(description = "计划集id")
    private String collectionId;

    @Schema(description = "是否包含空执行人")
    private boolean nullExecutorKey;

    @Override
    public String getSortString(String defaultColumn, String tableAliseName) {
        Map<String, String> sort = getSort();
        if (sort == null || sort.isEmpty()) {
            return null;
        }
        Map.Entry<String, String> entry = sort.entrySet().iterator().next();
        if (StringUtils.equals(entry.getKey(), "lastExecTime")) {
            String direction = StringUtils.equalsIgnoreCase(entry.getValue(), "DESC") ? "DESC" : "ASC";
            return "test_plan_functional_case.last_exec_time " + direction
                    + ",test_plan_functional_case.id " + direction;
        }
        return super.getSortString(defaultColumn, tableAliseName);
    }
}
