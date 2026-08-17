package io.metersphere.system.service;

import io.metersphere.sdk.constants.OperationLogConstants;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.WorkflowDefinition;
import io.metersphere.system.dto.permission.control.WorkflowDesignerDTO;
import io.metersphere.system.dto.permission.control.WorkflowMigrationRequest;
import io.metersphere.system.log.constants.OperationLogModule;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.log.dto.LogDTO;
import io.metersphere.system.mapper.PermissionControlMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class PermissionControlFlowLogService {
    @Resource private PermissionControlMapper mapper;

    public LogDTO add(WorkflowDefinition request) { return value(request.getId(), request.getName(), OperationLogType.ADD, request); }
    public LogDTO update(WorkflowDefinition request) { return flow(request.getId(), OperationLogType.UPDATE, request); }
    public LogDTO designer(String flowId, WorkflowDesignerDTO request) { return flow(flowId, OperationLogType.UPDATE, request); }
    public LogDTO publish(String flowId) { return flow(flowId, OperationLogType.UPDATE, "PUBLISH"); }
    public LogDTO activate(String flowId) { return flow(flowId, OperationLogType.UPDATE, "ACTIVATE_FOR_NEW_BUGS"); }
    public LogDTO archive(String flowId) { return flow(flowId, OperationLogType.UPDATE, "ARCHIVE"); }
    public LogDTO delete(String flowId) { return flow(flowId, OperationLogType.DELETE, "DELETE"); }
    public LogDTO syncPositions(String flowId) { return flow(flowId, OperationLogType.UPDATE, "SYNC_WECOM_POSITIONS"); }
    public LogDTO copy(String flowId) { return flow(flowId, OperationLogType.ADD, "COPY_VERSION"); }
    public LogDTO migration(WorkflowMigrationRequest request) { return flow(request.getTargetFlowId(), OperationLogType.UPDATE, request); }
    public LogDTO batch(String batchId) { return value(batchId, batchId, OperationLogType.UPDATE, batchId); }

    private LogDTO flow(String flowId, OperationLogType type, Object value) {
        WorkflowDefinition flow = mapper.selectWorkflowDefinitionById(flowId);
        return value(flowId, flow == null ? flowId : flow.getName(), type, value);
    }

    private LogDTO value(String id, String name, OperationLogType type, Object value) {
        LogDTO dto = new LogDTO(OperationLogConstants.SYSTEM, OperationLogConstants.SYSTEM, id, null,
                type.name(), OperationLogModule.SETTING_SYSTEM_USER_GROUP, "权限控制流程：" + name);
        dto.setOriginalValue(JSON.toJSONBytes(value));
        return dto;
    }
}
