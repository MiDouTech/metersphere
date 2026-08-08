package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionOperationsDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionOperationsService {
    private static final long RUNNER_STALE_MS = 90_000L;
    private static final long TASK_STUCK_MS = 10L * 60L * 1000L;

    @Resource
    private AgentExecutionMapper executionMapper;

    @Transactional(readOnly = true)
    public AgentExecutionOperationsDTO summary() {
        String organizationId = SessionUtils.getCurrentOrganizationId();
        if (StringUtils.isBlank(organizationId)) {
            throw new MSException("无法解析当前组织");
        }
        long now = System.currentTimeMillis();
        AgentExecutionOperationsDTO result = executionMapper.selectOperationsSummary(
                organizationId, now, now - RUNNER_STALE_MS, now - TASK_STUCK_MS);
        if (result == null) {
            result = new AgentExecutionOperationsDTO();
        }
        result.setOrganizationId(organizationId);
        result.setGeneratedAt(now);
        result.setHealth(isDegraded(result) ? "DEGRADED" : "HEALTHY");
        return result;
    }

    private boolean isDegraded(AgentExecutionOperationsDTO result) {
        return positive(result.getStaleRunnerCount()) || positive(result.getStuckTaskCount())
                || positive(result.getWritebackBacklogCount()) || positive(result.getArtifactBacklogCount());
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
