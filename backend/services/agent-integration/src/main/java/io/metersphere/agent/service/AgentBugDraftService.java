package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentBugCreateRequest;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AgentBugDraftService {
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentBugWriteService bugWriteService;

    public String createIfAbsent(AgentExecutionTaskDTO task, AgentExecutionCaseDTO executionCase, int evidenceCount) {
        String reservationId = reserve(task, executionCase, evidenceCount);
        List<Map<String,Object>> existing=jdbc.queryForList("SELECT status,bug_id FROM ai_execution_bug_draft WHERE id=?",reservationId);
        if(!existing.isEmpty() && "CREATED".equals(existing.getFirst().get("status")))return String.valueOf(existing.getFirst().get("bug_id"));
        try{
            AgentBugCreateRequest request=new AgentBugCreateRequest();request.setProjectId(task.getProjectId());request.setCaseId(executionCase.getCaseId());
            request.setTestPlanId(task.getTestPlanId());request.setTitle("[AI 草稿] "+StringUtils.defaultIfBlank(executionCase.getCaseName(),executionCase.getCaseId()));
            request.setDescription("AI 测试检测到有证据支撑的产品失败。任务："+task.getId()+"；执行用例："+executionCase.getId()+"；证据数："+evidenceCount+"；请人工复核后流转。");
            request.setTags(List.of("AI_DRAFT","AI_EXECUTION"));request.setRequestId("ai-bug-draft:"+task.getId()+":"+executionCase.getId());
            String bugId=bugWriteService.createDraft(request,StringUtils.defaultIfBlank(task.getExecutedBy(),task.getCreateUser())).getId();
            int changed=jdbc.update("UPDATE ai_execution_bug_draft SET bug_id=?,status='CREATED',error_code=NULL,update_time=? WHERE id=? AND status='CREATING'",bugId,System.currentTimeMillis(),reservationId);
            if(changed!=1)throw new MSException("BUG_DRAFT_RESERVATION_LOST");
            return bugId;
        }catch(RuntimeException ex){jdbc.update("UPDATE ai_execution_bug_draft SET status='FAILED',error_code='BUG_DRAFT_CREATE_FAILED',update_time=? WHERE id=?",System.currentTimeMillis(),reservationId);throw ex;}
    }

    private String reserve(AgentExecutionTaskDTO task, AgentExecutionCaseDTO executionCase, int evidenceCount) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,status,bug_id FROM ai_execution_bug_draft WHERE task_id=? AND execution_case_id=?",task.getId(),executionCase.getId());
        if(!rows.isEmpty()){
            Map<String,Object> row=rows.getFirst();String status=String.valueOf(row.get("status"));
            if("CREATED".equals(status))return String.valueOf(row.get("id"));
            if("FAILED".equals(status)){
                int changed=jdbc.update("UPDATE ai_execution_bug_draft SET status='CREATING',error_code=NULL,evidence_count=?,update_time=? WHERE id=? AND status='FAILED'",evidenceCount,System.currentTimeMillis(),row.get("id"));
                if(changed==1)return String.valueOf(row.get("id"));
            }
            throw new MSException("BUG_DRAFT_CREATION_IN_PROGRESS");
        }
        String reservationId=IDGenerator.nextStr();long now=System.currentTimeMillis();
        try{
            jdbc.update("INSERT INTO ai_execution_bug_draft(id,task_id,execution_case_id,case_id,status,evidence_count,create_time,update_time) VALUES (?,?,?,?, 'CREATING',?,?,?)",
                    reservationId,task.getId(),executionCase.getId(),executionCase.getCaseId(),evidenceCount,now,now);
            return reservationId;
        }catch(DuplicateKeyException conflict){
            throw new MSException("BUG_DRAFT_CREATION_IN_PROGRESS");
        }
    }
}
