package io.metersphere.project.request;

import io.metersphere.system.dto.sdk.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ProjectPageRequest extends BasePageRequest {
    @Schema(description = "项目名称或项目 ID")
    private String keyword;

    @Schema(description = "启用状态")
    private Boolean enable;
}
