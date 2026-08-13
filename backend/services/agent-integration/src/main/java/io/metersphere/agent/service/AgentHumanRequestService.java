package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentHumanCreateRequest;
import io.metersphere.agent.dto.AgentHumanRequestDTO;
import io.metersphere.agent.dto.AgentHumanResponseRequest;
import io.metersphere.agent.mapper.AgentHumanRequestMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentHumanRequestService {
    private static final Set<String> REQUEST_TYPES = Set.of("APPROVAL", "INPUT", "LOGIN", "MANUAL_STEP", "REVIEW");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    @Resource
    private AgentHumanRequestMapper mapper;

    public AgentHumanRequestDTO create(String taskId, String projectId, String type, String title,
                                       String content, String riskLevel, String requestedBy,
                                       String assignedTo, Long expiresAt) {
        long now = System.currentTimeMillis();
        AgentHumanRequestDTO request = new AgentHumanRequestDTO();
        request.setId(IDGenerator.nextStr());
        request.setTaskId(taskId);
        request.setProjectId(projectId);
        request.setRequestType(type);
        request.setTitle(title);
        request.setContent(content);
        request.setRiskLevel(riskLevel);
        request.setStatus("PENDING");
        request.setRequestedBy(requestedBy);
        request.setAssignedTo(assignedTo);
        request.setExpiresAt(expiresAt);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        mapper.insert(request);
        return request;
    }

    public AgentHumanRequestDTO createFromAgent(String taskId, String projectId, AgentHumanCreateRequest source,
                                                String requestedBy, String assignedTo) {
        String requestKey = StringUtils.trim(source.getRequestId());
        AgentHumanRequestDTO existing = mapper.selectByTaskAndKey(taskId, requestKey);
        if (existing != null) {
            return existing;
        }
        String requestType = StringUtils.upperCase(StringUtils.trim(source.getRequestType()));
        if (!REQUEST_TYPES.contains(requestType)) {
            throw new MSException("requestType 仅支持 " + String.join("/", REQUEST_TYPES));
        }
        String riskLevel = StringUtils.upperCase(StringUtils.defaultIfBlank(source.getRiskLevel(), "MEDIUM"));
        if (!RISK_LEVELS.contains(riskLevel)) {
            throw new MSException("riskLevel 仅支持 LOW/MEDIUM/HIGH/CRITICAL");
        }
        long now = System.currentTimeMillis();
        AgentHumanRequestDTO request = new AgentHumanRequestDTO();
        request.setId(IDGenerator.nextStr());
        request.setRequestKey(requestKey);
        request.setTaskId(taskId);
        request.setProjectId(projectId);
        request.setRequestType(requestType);
        request.setTitle(StringUtils.trim(source.getTitle()));
        request.setContent(StringUtils.abbreviate(source.getContent(), 4000));
        request.setRiskLevel(riskLevel);
        request.setStatus("PENDING");
        request.setRequestedBy(requestedBy);
        request.setAssignedTo(assignedTo);
        request.setExpiresAt(source.getExpiresAt());
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        mapper.insert(request);
        return mapper.selectByTaskAndKey(taskId, requestKey);
    }

    public List<AgentHumanRequestDTO> list(String taskId) {
        return mapper.selectByTaskId(taskId);
    }

    public AgentHumanRequestDTO respond(String taskId, String id, AgentHumanResponseRequest request, String userId) {
        AgentHumanRequestDTO existing = mapper.selectById(id);
        if (existing == null) {
            throw new MSException("人工介入请求不存在: " + id);
        }
        if (!StringUtils.equals(taskId, existing.getTaskId())) {
            throw new MSException("人工介入请求不属于当前任务");
        }
        String status = switch (StringUtils.upperCase(request.getAction())) {
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            case "ANSWER" -> "ANSWERED";
            case "CANCEL" -> "CANCELED";
            default -> throw new MSException("action 仅支持 APPROVE/REJECT/ANSWER/CANCEL");
        };
        if ("ANSWERED".equals(status) && StringUtils.isBlank(request.getResponse())) {
            throw new MSException("补充输入不能为空");
        }
        int updated = mapper.respond(id, status, StringUtils.abbreviate(request.getResponse(), 4000),
                userId, System.currentTimeMillis());
        if (updated != 1) {
            throw new MSException("请求已被处理，请刷新后查看");
        }
        return mapper.selectById(id);
    }

    public void closePending(String taskId, String type, String status, String response, String userId) {
        mapper.closePendingByTaskAndType(taskId, type, status, StringUtils.abbreviate(response, 4000),
                userId, System.currentTimeMillis());
    }
}
