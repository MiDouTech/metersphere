package io.metersphere.functional.dto;

import io.metersphere.system.dto.table.TableBatchProcessDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @author wx
 */
@Data
public class BaseFunctionalCaseBatchDTO extends TableBatchProcessDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "workspace id")
    private String workspaceId;

    @Schema(description = "dimension: PROJECT/SYSTEM")
    private String dimension = "PROJECT";

    @Schema(description = "project ids")
    private List<String> projectIds;

    @Schema(description = "include cases without project")
    private Boolean unassignedProject;

    @Schema(description = "business system id")
    private String systemId;

    @Schema(description = "business system module id")
    private String systemModuleId;

    @Schema(description = "include unclassified system cases")
    private Boolean unclassifiedSystem;

    @Schema(description = "模块id")
    private List<String> moduleIds;

    @Schema(description = "版本id")
    private String versionId;

    @Schema(description = "版本来源")
    private String refId;
}
