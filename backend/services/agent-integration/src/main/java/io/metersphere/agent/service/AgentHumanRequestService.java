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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentHumanRequestService {
    private static final Set<String> REQUEST_TYPES = Set.of("APPROVAL", "INPUT", "LOGIN", "MANUAL_STEP", "REVIEW");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    @Resource
    private AgentHumanRequestMapper mapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

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
        Integer recipients = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ai_human_request_recipient WHERE request_id=?", Integer.class, id);
        if (recipients != null && recipients > 0) {
            return respondFirstWins(taskId, id, userId, status, request.getResponse(), request.getExpectedVersion());
        }
        int updated = mapper.respond(id, status, StringUtils.abbreviate(request.getResponse(), 4000), userId, System.currentTimeMillis());
        if (updated != 1) throw new MSException("ALREADY_RESOLVED");
        return mapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentHumanRequestDTO createForRecipients(String taskId, String projectId, String type, String title,
                                                     String content, String riskLevel, String requestedBy,
                                                     List<String> recipientUserIds, boolean checkpointRequired,
                                                     Long expiresAt, String actionHash) {
        List<String> recipients = recipientUserIds == null ? List.of() : recipientUserIds.stream()
                .filter(StringUtils::isNotBlank).map(String::trim).distinct().toList();
        if (recipients.size() != 3) throw new MSException("RESPONSIBLE_USERS_MUST_BE_EXACTLY_THREE");
        String placeholders = String.join(",", java.util.Collections.nCopies(3, "?"));
        List<Object> args = new ArrayList<>(recipients); args.add(projectId);
        Integer valid = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT u.id) FROM user u JOIN user_role_relation r ON r.user_id=u.id WHERE u.id IN ("+placeholders+") AND u.enable=1 AND u.deleted=0 AND r.source_id=?", Integer.class, args.toArray());
        if (valid == null || valid != 3) throw new MSException("RESPONSIBLE_USER_INVALID_OR_UNAUTHORIZED");
        long now=System.currentTimeMillis(); String id=IDGenerator.nextStr(); String traceId=UUID.randomUUID().toString();
        jdbcTemplate.update("""
          INSERT INTO ai_execution_human_request(id,request_key,task_id,project_id,request_type,title,content,risk_level,
          status,resolution_version,requested_by,assigned_to,expires_at,trace_id,created_at,updated_at)
          VALUES (?,?,?,?,?,?,?,?, 'PENDING',0,?,NULL,?,?,?,?)
          """,id,actionHash,taskId,projectId,type,StringUtils.abbreviate(title,255),StringUtils.abbreviate(content,4000),riskLevel,
                requestedBy,expiresAt,traceId,now,now);
        for(String user:recipients){jdbcTemplate.update("INSERT INTO ai_human_request_recipient(id,request_id,user_id,notify_status,response_status,create_time) VALUES (?,?,?,'PENDING','PENDING',?)",IDGenerator.nextStr(),id,user,now);}
        notifyAllRecipients(id,taskId,projectId,title,content,requestedBy,recipients,now);
        AgentHumanRequestDTO result=mapper.selectById(id);result.setRecipientUserIds(recipients);return result;
    }

    public AgentHumanRequestDTO createForTaskRecipients(String taskId,String type,String title,String content,String riskLevel,
                                                         String requestedBy,boolean checkpointRequired,Long expiresAt,String actionHash){
        Map<String,Object> row=jdbcTemplate.queryForMap("SELECT t.project_id,p.request_json FROM ai_execution_task t JOIN ai_execution_preflight p ON p.id=t.preflight_id WHERE t.id=?",taskId);
        Map<String,Object> request=io.metersphere.sdk.util.JSON.parseObject((String)row.get("request_json"),Map.class);
        @SuppressWarnings("unchecked") List<String> users=(List<String>)request.get("responsibleUserIds");
        return createForRecipients(taskId,(String)row.get("project_id"),type,title,content,riskLevel,requestedBy,users,checkpointRequired,expiresAt,actionHash);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentHumanRequestDTO respondFirstWins(String taskId,String requestId,String userId,String status,String response,Integer expectedVersion){
        AgentHumanRequestDTO current=mapper.selectById(requestId);if(current==null||!taskId.equals(current.getTaskId()))throw new MSException("HUMAN_REQUEST_NOT_FOUND");
        Integer allowed=jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ai_human_request_recipient WHERE request_id=? AND user_id=?",Integer.class,requestId,userId);
        if(allowed==null||allowed!=1)throw new MSException("HUMAN_REQUEST_FORBIDDEN");
        int expected=expectedVersion==null?(current.getResolutionVersion()==null?0:current.getResolutionVersion()):expectedVersion;long now=System.currentTimeMillis();
        int changed=jdbcTemplate.update("UPDATE ai_execution_human_request SET status=?,response=?,responded_by=?,responded_at=?,resolution_version=resolution_version+1,updated_at=? WHERE id=? AND task_id=? AND status='PENDING' AND resolution_version=?",
                status,StringUtils.abbreviate(response,4000),userId,now,now,requestId,taskId,expected);
        if(changed!=1)throw new MSException("ALREADY_RESOLVED");
        jdbcTemplate.update("UPDATE ai_human_request_recipient SET response_status=CASE WHEN user_id=? THEN 'RESPONDED' ELSE 'CLOSED' END,responded_at=CASE WHEN user_id=? THEN ? ELSE responded_at END WHERE request_id=?",userId,userId,now,requestId);
        notifyResolution(requestId,taskId,current.getProjectId(),userId,status,now);
        AgentHumanRequestDTO result=mapper.selectById(requestId);result.setRecipientUserIds(jdbcTemplate.queryForList("SELECT user_id FROM ai_human_request_recipient WHERE request_id=? ORDER BY create_time",String.class,requestId));return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public int expirePendingRequests(long now){
        List<Map<String,Object>> rows=jdbcTemplate.queryForList("SELECT id,task_id,project_id FROM ai_execution_human_request WHERE status='PENDING' AND expires_at IS NOT NULL AND expires_at<=? FOR UPDATE",now);
        for(Map<String,Object> row:rows){jdbcTemplate.update("UPDATE ai_execution_human_request SET status='EXPIRED',resolution_version=resolution_version+1,resolved_reason='HUMAN_REQUEST_TIMEOUT',updated_at=? WHERE id=? AND status='PENDING'",now,row.get("id"));jdbcTemplate.update("UPDATE ai_execution_task SET status='EXPIRED',blocked_reason='BLOCKED_POLICY',blocked_detail='HUMAN_REQUEST_TIMEOUT',update_time=? WHERE id=? AND status='WAITING_HUMAN'",now,row.get("task_id"));}
        return rows.size();
    }

    private void notifyAllRecipients(String requestId,String taskId,String projectId,String title,String content,String operator,List<String> users,long now){
        String org=jdbcTemplate.queryForObject("SELECT organization_id FROM project WHERE id=?",String.class,projectId);
        for(String user:users){jdbcTemplate.update("INSERT INTO notification(type,receiver,subject,status,create_time,operator,operation,resource_id,project_id,organization_id,resource_type,resource_name,content) VALUES ('AI_EXECUTION',?,?,'UNREAD',?,?,'HUMAN_REQUEST',?,?,?,'AI_EXECUTION_HUMAN_REQUEST',?,?)",user,title,now,operator,requestId,projectId,org,taskId,StringUtils.abbreviate(content,4000));jdbcTemplate.update("UPDATE ai_human_request_recipient SET notify_status='DELIVERED',notified_at=? WHERE request_id=? AND user_id=?",now,requestId,user);}
    }
    private void notifyResolution(String requestId,String taskId,String projectId,String responder,String status,long now){jdbcTemplate.update("UPDATE notification SET status='READ' WHERE resource_id=? AND resource_type='AI_EXECUTION_HUMAN_REQUEST'",requestId);}

    public void closePending(String taskId, String type, String status, String response, String userId) {
        mapper.closePendingByTaskAndType(taskId, type, status, StringUtils.abbreviate(response, 4000),
                userId, System.currentTimeMillis());
    }
}
