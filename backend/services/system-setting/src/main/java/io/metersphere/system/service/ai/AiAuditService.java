package io.metersphere.system.service.ai;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.log.constants.OperationLogModule;
import io.metersphere.system.log.dto.LogDTO;
import io.metersphere.system.log.service.OperationLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class AiAuditService {
    @Resource private OperationLogService operationLogService;

    public void record(String projectId, String organizationId, String userId, String sourceId,
                       String type, String action, String path, String method, Map<String, ?> detail) {
        try {
            LogDTO logDTO = new LogDTO(projectId, organizationId, sourceId, userId, type,
                    OperationLogModule.AI_PROVIDER_GOVERNANCE, action);
            logDTO.setPath(path);
            logDTO.setMethod(method);
            logDTO.setModifiedValue(JSON.toJSONString(detail == null ? Map.of() : detail).getBytes(StandardCharsets.UTF_8));
            operationLogService.add(logDTO);
        } catch (Exception ex) {
            log.warn("AI audit persistence failed action={}, sourceId={}, error={}", action, sourceId,
                    StringUtils.defaultString(ex.getMessage()));
        }
    }
}
