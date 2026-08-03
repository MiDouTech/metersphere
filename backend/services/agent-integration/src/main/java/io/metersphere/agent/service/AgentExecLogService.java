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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentExecLogService {
    private static final long EXPORT_MAX = 5000;

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
        long total = agentExecLogMapper.countPage(request.getCaseId(), request.getExecutedBy(),
                request.getAction(), request.getCreateUser());
        List<AgentExecLogDTO> list = agentExecLogMapper.selectPage(request.getCaseId(), request.getExecutedBy(),
                        request.getAction(), request.getCreateUser(), offset, pageSize)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return new Pager<>(list, total, pageSize, current);
    }

    public byte[] exportCsv(AgentExecLogPageRequest request) {
        List<AgentExecLog> logs = agentExecLogMapper.selectExport(
                request.getCaseId(), request.getExecutedBy(), request.getAction(), request.getCreateUser(), EXPORT_MAX);
        StringBuilder sb = new StringBuilder();
        sb.append("id,caseId,action,executedBy,createUser,createTime,content\n");
        for (AgentExecLog log : logs) {
            sb.append(csv(log.getId())).append(',')
                    .append(csv(log.getCaseId())).append(',')
                    .append(csv(log.getLastExecResult())).append(',')
                    .append(csv(log.getExecutedBy())).append(',')
                    .append(csv(log.getCreateUser())).append(',')
                    .append(log.getCreateTime() == null ? "" : log.getCreateTime()).append(',')
                    .append(csv(log.getContent()))
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
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

    private static String csv(String value) {
        String raw = StringUtils.defaultString(value).replace("\"", "\"\"");
        return "\"" + raw + "\"";
    }
}
