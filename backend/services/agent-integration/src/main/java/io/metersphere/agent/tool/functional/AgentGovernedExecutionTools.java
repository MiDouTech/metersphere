package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentProtocolToolService;
import io.metersphere.agent.service.AgentSessionProtocolService;
import io.metersphere.agent.tool.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class AgentGovernedExecutionTools implements AgentMcpToolContributor {
    private final AgentProtocolToolService service;
    public AgentGovernedExecutionTools(AgentProtocolToolService service){this.service=service;}
    public List<AgentMcpToolHandler> tools() {
        var operations=new ArrayList<>(AgentProtocolToolService.OPERATIONS);operations.addAll(List.of("knowledge.search","knowledge.get","knowledge.save","evidence.upload"));
        return operations.stream().map(operation -> (AgentMcpToolHandler)new AgentMcpToolHandler() {
            public String name(){return operation.startsWith("knowledge.")&&Set.of("knowledge.search","knowledge.get","knowledge.save").contains(operation)?"metersphere.functional."+operation:AgentSessionProtocolService.TOOL_PREFIX+operation;}
            public String requiredScope(){return operation.startsWith("knowledge.")&&Set.of("knowledge.search","knowledge.get","knowledge.save").contains(operation)?operation.endsWith("save")?AgentTokenScope.KNOWLEDGE_EDIT_V1:AgentTokenScope.KNOWLEDGE_READ_V1:readOnlyHint()?AgentTokenScope.SESSION_READ_V1:AgentTokenScope.SESSION_EXECUTE_V1;}
            public String description(){return "Governed execution protocol 1.0: "+operation+". Read contract first; new writes require explicit configuration and authorization. Prepare before any external business write; UNKNOWN is not failure. Human review/approval operations require the trusted human UI and cannot be approved by an Agent. Repeat requests with the same key AND original payload; an old receipt is not a current execution permit.";}
            public boolean readOnlyHint(){return Set.of("checkpoint.get","action.status","result.status.get","result.history.get","knowledge.search","knowledge.get").contains(operation);}
            public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),readOnlyHint(),false,true);}
            public Map<String,Object> inputSchema(){return schema(operation,readOnlyHint());}
            public Object execute(Map<String,Object> args){return service.execute(operation,args);}
        }).toList();
    }
    private Map<String,Object> schema(String operation,boolean read) {
        var properties=new LinkedHashMap<String,Object>();
        for(String key:List.of("protocolVersion","idempotencyKey","sessionId","caseExecutionId","projectId","caseId","sourceType","taskId","environmentProfileId","governanceUserId","organizationId","knowledgeId","stepId","actionId","actionType","target","expectedEffect","businessKey","queryMethod","resolution","objectId","queryEvidence","key","observation","outcome","failureEventId","changedCondition","proposedAction","expectedObservation","condition","reason","requestId","result","keyword","scope","system","module","type"))properties.put(key,Map.of("type","string","minLength",1));
        properties.put("protocolVersion",Map.of("type","string","const","1.0"));
        properties.put("environmentId",Map.of("type","string","minLength",1));
        properties.put("targetUserId",Map.of("type","string","minLength",1));
        properties.put("proposalHash",Map.of("type","string","minLength",1));
        properties.put("newKnowledgeId",Map.of("type","string","minLength",1));
        for(String key:List.of("targetEntity","retryOf","recoveryPermitId","unverifiableReason"))properties.put(key,Map.of("type","string","minLength",1));
        properties.put("sourceLeaseId",Map.of("type","string","minLength",1));properties.put("noLegacyBusinessActionStarted",Map.of("type","boolean"));
        properties.put("expectedKnowledgeVersion",Map.of("type","integer","minimum",0));
        for(String key:List.of("contentType","fileName","base64","purpose"))properties.put(key,Map.of("type","string","minLength",1));
        for(String key:List.of("expectedContextVersion","leaseGeneration","expectedSessionVersion","executionContextVersion","expectedResultVersion","resultVersion","submissionRevision","originalGeneration","contractVersion","knowledgeVersion","oldVersion","newVersion","version","page","size"))properties.put(key,Map.of("type","integer","minimum",0));
        for(String key:List.of("parameters","terminalProof","conditions","variables","preflight","dependencyInstances","outputs","difference","content"))properties.put(key,AgentMcpToolSchemas.objectAny());
        for(String key:List.of("assertions","evidence","stepEvidence","correctedSteps","legacyAssertions","attempts"))properties.put(key,Map.of("type","array","items",AgentMcpToolSchemas.objectAny()));
        properties.put("clientCapabilities",Map.of("type","array","items",Map.of("type","string")));properties.put("memoryOnlyCache",Map.of("type","boolean"));properties.put("retainUnknown",Map.of("type","boolean"));properties.put("redacted",Map.of("type","boolean"));
        var required=new ArrayList<String>();if(!read||Set.of("knowledge.search","knowledge.get").contains(operation))required.addAll(List.of("protocolVersion","idempotencyKey"));
        if(operation.startsWith("knowledge.")&&Set.of("knowledge.search","knowledge.get","knowledge.save").contains(operation))required.add("organizationId");
        else if("start".equals(operation))required.addAll(List.of("projectId","sourceType","environmentProfileId","governanceUserId","clientCapabilities","memoryOnlyCache"));
        else required.add("sessionId");
        switch(operation){
            case "start" -> { }
            case "resume","checkpoint.get" -> { }
            case "finish","governance.transfer" -> required.add("expectedSessionVersion");
            case "context.save" -> required.addAll(List.of("executionContextVersion","variables"));
            case "knowledge.search" -> required.addAll(List.of("sessionId","caseExecutionId","page","size"));
            case "knowledge.get" -> required.addAll(List.of("sessionId","caseExecutionId","knowledgeId","version"));
            case "knowledge.save" -> required.addAll(List.of("environmentId","content"));
            default -> required.add("caseExecutionId");
        }
        if(List.of("lease.takeover","case.prepare","action.prepare","data.record","step.save","recovery.record","human.request","case.finish","knowledge.adopt","cleanup.prepare","cleanup.recovery.record","cleanup.lease.takeover").contains(operation))required.addAll(List.of("expectedContextVersion","leaseGeneration"));
        if(operation.startsWith("result.")&&!Set.of("result.status.get","result.history.get").contains(operation)||"cancel".equals(operation))required.add("expectedResultVersion");
        switch(operation){
            case "action.prepare","cleanup.prepare" -> required.addAll(List.of("actionType","targetEntity","target","parameters","expectedEffect","businessKey","queryMethod"));
            case "action.resolve","cleanup.resolve" -> required.addAll(List.of("actionId","resolution"));
            case "case.prepare","result.revalidate" -> required.add("preflight");
            case "data.record" -> required.addAll(List.of("key","objectId","conditions","evidence"));
            case "step.save" -> required.addAll(List.of("stepId","observation","outcome","evidence"));
            case "recovery.record","cleanup.recovery.record" -> required.addAll(List.of("failureEventId","observation","changedCondition","proposedAction","expectedObservation","evidence"));
            case "human.request" -> required.addAll(List.of("condition","reason"));
            case "human.resolve" -> required.addAll(List.of("requestId","resolution"));
            case "case.finish" -> required.addAll(List.of("result","assertions"));
            case "result.evidence.append" -> required.addAll(List.of("submissionRevision","assertions"));
            case "result.history.get" -> required.add("resultVersion");
            case "result.close-unaccepted" -> required.addAll(List.of("submissionRevision","reason"));
            case "result.correction.request","cancel" -> required.add("reason");
            case "governance.transfer" -> required.addAll(List.of("targetUserId","reason"));
            case "knowledge.adopt" -> required.addAll(List.of("knowledgeId","knowledgeVersion","stepId","purpose"));
            case "knowledge.substitution.propose","knowledge.substitution.confirm" -> {required.addAll(List.of("knowledgeId","oldVersion","newVersion"));if(operation.endsWith("confirm"))required.addAll(List.of("proposalHash","reason"));else required.add("difference");}
            case "observation.append" -> required.addAll(List.of("actionId","originalGeneration","observation","evidence"));
            case "evidence.upload" -> required.addAll(List.of("fileName","contentType","base64","redacted","purpose"));
            default -> { }
        }
        // Runtime services validate each operation's remaining exact field set and nested business schema.
        return AgentMcpToolSchemas.object(properties,required.stream().distinct().toList());
    }
}
