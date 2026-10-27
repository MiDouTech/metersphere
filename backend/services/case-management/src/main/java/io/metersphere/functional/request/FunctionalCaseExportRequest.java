package io.metersphere.functional.request;

import io.metersphere.functional.dto.BaseFunctionalCaseBatchDTO;
import io.metersphere.functional.excel.domain.FunctionalCaseHeader;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wx
 */
@Data
public class FunctionalCaseExportRequest extends BaseFunctionalCaseBatchDTO implements Serializable {
    @jakarta.validation.constraints.Size(max=50)
    private String assetCatalogId;
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "系统字段", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<FunctionalCaseHeader> systemFields = new ArrayList<>();

    @Schema(description = "自定义字段")
    private List<FunctionalCaseHeader> customFields = new ArrayList<>();

    @Schema(description = "其他字段")
    private List<FunctionalCaseHeader> otherFields = new ArrayList<>();

    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String projectId;

    @Schema(description = "文件id")
    private String fileId;

    @jakarta.validation.constraints.Size(max = 64)
    @Schema(description = "客户端幂等请求标识")
    private String requestId;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @Schema(description = "服务端关联的原导出任务，只读")
    private String sourceTaskId;

    @Schema(description = "单元格拆分")
    private Boolean isMerge = false;

}
