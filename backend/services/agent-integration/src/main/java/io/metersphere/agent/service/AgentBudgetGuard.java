package io.metersphere.agent.service;
import io.metersphere.agent.dto.AgentModelProfileDTO;import io.metersphere.sdk.exception.MSException;import jakarta.annotation.Resource;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import java.math.BigDecimal;
@Service public class AgentBudgetGuard {
 @Resource private AgentModelInvocationService invocations;@Resource private JdbcTemplate jdbc;
 public void checkBeforeInvoke(AgentModelProfileDTO profile,String taskId){if(profile.getMaxCostAmount()!=null&&invocations.taskCost(taskId).compareTo(profile.getMaxCostAmount())>=0)blockOnExceeded(taskId);Integer calls=jdbc.queryForObject("SELECT COUNT(1) FROM ai_model_invocation WHERE task_id=?",Integer.class,taskId);if(calls!=null&&calls>=100)throw new MSException("MODEL_INVOCATION_LIMIT_EXCEEDED");}
 public void recordAfterInvoke(AgentModelProfileDTO profile,String taskId){if(profile.getMaxCostAmount()!=null&&invocations.taskCost(taskId).compareTo(profile.getMaxCostAmount())>0)blockOnExceeded(taskId);}
 public void blockOnExceeded(String taskId){jdbc.update("UPDATE ai_execution_task SET blocked_reason='BLOCKED_MODEL',blocked_detail='MODEL_BUDGET_EXCEEDED',verdict='BLOCKED',verdict_reason='MODEL_BUDGET_EXCEEDED',update_time=? WHERE id=?",System.currentTimeMillis(),taskId);throw new MSException("MODEL_BUDGET_EXCEEDED");}
}
