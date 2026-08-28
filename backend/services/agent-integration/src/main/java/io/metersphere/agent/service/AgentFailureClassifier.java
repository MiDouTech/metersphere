package io.metersphere.agent.service;
import io.metersphere.agent.constants.AgentExecutionVerdict;import io.metersphere.agent.dto.AgentExecutionStepDTO;import org.apache.commons.lang3.StringUtils;import org.springframework.stereotype.Service;import java.util.*;
@Service public class AgentFailureClassifier {
 public Classification classify(List<AgentExecutionStepDTO> steps,int artifactCount,boolean cleanupIncomplete,boolean writebackFailed){
  if(cleanupIncomplete)return new Classification(AgentExecutionVerdict.DATA_FAILED,false,"TEST_DATA_CLEANUP_INCOMPLETE");
  if(writebackFailed)return new Classification(AgentExecutionVerdict.AGENT_FAILED,false,"WRITEBACK_FAILED");
  List<String> categories=steps==null?List.of():steps.stream().map(AgentExecutionStepDTO::getFailureCategory).filter(StringUtils::isNotBlank).map(String::toUpperCase).toList();
  if(categories.stream().anyMatch(v->v.startsWith("AUTH_")||v.contains("CREDENTIAL")))return new Classification("AUTH_FAILED",artifactCount>0,"AUTHENTICATION_FAILURE");
  if(categories.stream().anyMatch(v->v.startsWith("ENV_")||v.startsWith("NETWORK_")))return new Classification(AgentExecutionVerdict.ENV_FAILED,artifactCount>0,"ENVIRONMENT_FAILURE");
  if(categories.stream().anyMatch(v->v.startsWith("DATA_")||v.contains("CLEANUP")))return new Classification(AgentExecutionVerdict.DATA_FAILED,artifactCount>0,"TEST_DATA_FAILURE");
  if(categories.stream().anyMatch(v->v.startsWith("LOCATOR_")))return new Classification("LOCATOR_FAILED",artifactCount>0,"LOCATOR_FAILURE");
  if(categories.stream().anyMatch(v->v.startsWith("AGENT_PLAN_")))return new Classification("AGENT_PLAN_FAILED",false,"PLANNING_FAILURE");
  if(categories.stream().anyMatch(v->v.startsWith("RUNNER_")))return new Classification("RUNNER_FAILED",false,"RUNNER_FAILURE");
  if(categories.stream().anyMatch(v->v.startsWith("AGENT_")||v.startsWith("SCOPE_")))return new Classification(AgentExecutionVerdict.AGENT_FAILED,false,"AGENT_FAILURE");
  boolean sufficient=artifactCount>0;return new Classification(sufficient?AgentExecutionVerdict.PRODUCT_FAILED:"NEEDS_REVIEW",sufficient,sufficient?"PRODUCT_ASSERTION_FAILED":"PRODUCT_FAILURE_EVIDENCE_MISSING");
 }
 public record Classification(String verdict,boolean evidenceSufficient,String reason){}
}
