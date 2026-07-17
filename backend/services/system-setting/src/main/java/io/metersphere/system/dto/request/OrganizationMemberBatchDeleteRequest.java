package io.metersphere.system.dto.request;

import io.metersphere.system.dto.table.TableBatchProcessDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织成员批量移出请求
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class OrganizationMemberBatchDeleteRequest extends TableBatchProcessDTO {

    @Schema(description = "组织ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{organization.id.not_blank}")
    private String organizationId;
}
