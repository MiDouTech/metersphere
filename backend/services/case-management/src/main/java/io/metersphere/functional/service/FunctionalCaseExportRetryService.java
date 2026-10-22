package io.metersphere.functional.service;

import io.metersphere.functional.dto.ExportTaskDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** Web and MCP share authorization, original parameters, lineage and idempotent creation. */
@Service
public class FunctionalCaseExportRetryService {
    @Resource private FunctionalCaseExportTaskService tasks;
    @Resource private FunctionalCaseFileService files;
    @Resource private FunctionalCaseXmindService xmind;

    public ExportTaskDTO retry(String sourceId, String userId, String organizationId, String requestId) {
        ExportTaskDTO existing = tasks.existingRetry(sourceId, userId, requestId);
        if (existing != null) return existing;
        var parameters = tasks.prepareRetry(sourceId, userId, requestId);
        String id = "xmind".equals(parameters.format())
                ? xmind.exportFunctionalCaseXmind(parameters.request(), userId, organizationId)
                : files.export(userId, parameters.request(), organizationId);
        return tasks.get(id, userId);
    }
}
