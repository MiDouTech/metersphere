package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import io.metersphere.agent.dto.AgentExecLogDTO;
import io.metersphere.agent.dto.AgentExecLogPageRequest;
import io.metersphere.system.domain.AgentExecLog;
import io.metersphere.system.mapper.AgentExecLogMapper;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentExecLogService {
    @Resource
    private AgentExecLogMapper agentExecLogMapper;
    @Resource
    private AgentAttachmentService agentAttachmentService;

    public String log(AgentCaseSubmitRequest request, String stepsSnapshot) {
        AgentExecLog log = new AgentExecLog();
        log.setId(IDGenerator.nextStr());
        log.setCaseId(request.getCaseId());
        log.setTestPlanId(request.getTestPlanId());
        log.setTestPlanCaseId(request.getTestPlanCaseId());
        log.setLastExecResult(request.getLastExecResult());
        log.setExecutedBy(request.getExecutedBy());
        log.setStepsSnapshot(stepsSnapshot);
        log.setContent(formatContent(request.getExecutedBy(), request.getContent()));
        log.setCreateTime(System.currentTimeMillis());
        log.setCreateUser(SessionUtils.getUserId());
        agentExecLogMapper.insert(log);
        return log.getId();
    }

    /**
     * 高危写操作审计（复用 agent_exec_log；case_id 存资源 ID，last_exec_result 存动作码）。
     */
    public String audit(String action, String resourceId, String content) {
        AgentExecLog log = new AgentExecLog();
        log.setId(IDGenerator.nextStr());
        log.setCaseId(StringUtils.defaultIfBlank(resourceId, "AUDIT"));
        log.setLastExecResult(StringUtils.defaultIfBlank(action, "AUDIT"));
        log.setExecutedBy("agent-audit");
        log.setContent(content);
        log.setCreateTime(System.currentTimeMillis());
        log.setCreateUser(SessionUtils.getUserId());
        agentExecLogMapper.insert(log);
        return log.getId();
    }

    public Pager<List<AgentExecLogDTO>> page(AgentExecLogPageRequest request) {
        long current = Math.max(request.getCurrent(), 1);
        long pageSize = Math.max(request.getPageSize(), 1);
        long offset = (current - 1) * pageSize;
        long total = agentExecLogMapper.countPage(request.getCaseId(), request.getExecutedBy());
        List<AgentExecLogDTO> list = agentExecLogMapper.selectPage(request.getCaseId(), request.getExecutedBy(), offset, pageSize)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return new Pager<>(list, total, pageSize, current);
    }

    public AgentExecLogDTO get(String id) {
        AgentExecLog log = agentExecLogMapper.selectByPrimaryKey(id);
        if (log == null) {
            return null;
        }
        AgentExecLogDTO dto = toDto(log);
        dto.setAttachments(agentAttachmentService.listByExecLogId(id));
        return dto;
    }

    private AgentExecLogDTO toDto(AgentExecLog source) {
        AgentExecLogDTO target = new AgentExecLogDTO();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private String formatContent(String executedBy, String content) {
        if (StringUtils.isNotBlank(executedBy)) {
            return "[" + executedBy + "] " + StringUtils.defaultString(content);
        }
        return content;
    }
}
