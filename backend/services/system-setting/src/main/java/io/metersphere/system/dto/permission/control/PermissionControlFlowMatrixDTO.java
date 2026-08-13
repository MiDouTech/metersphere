package io.metersphere.system.dto.permission.control;

import io.metersphere.system.domain.StatusFlow;
import io.metersphere.system.dto.StatusItemDTO;
import lombok.Data;

import java.util.List;

@Data
public class PermissionControlFlowMatrixDTO {

    private List<StatusItemDTO> statuses;

    private List<StatusFlow> transitions;
}
