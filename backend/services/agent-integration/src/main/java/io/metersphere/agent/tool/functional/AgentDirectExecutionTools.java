package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentDirectExecutionService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.*;
import java.util.function.Function;

@Configuration
public class AgentDirectExecutionTools {
    @Bean public AgentMcpToolHandler directExecutionContract(AgentDirectExecutionService service) {
        return handler("contract",service::contract);
    }
    @Bean public AgentMcpToolHandler directExecutionUpload(AgentDirectExecutionService service) {
        return handler("evidence.upload",service::upload);
    }
    @Bean public AgentMcpToolHandler directExecutionSubmit(AgentDirectExecutionService service) {
        return handler("submit",service::submit);
    }
    @Bean public AgentMcpToolHandler directExecutionReport(AgentDirectExecutionService service) {
        return handler("get",service::report);
    }
    private AgentMcpToolHandler handler(String action,Function<Map<String,Object>,Object> execute) {
        boolean read=Set.of("contract","get").contains(action);
        return new AgentMcpToolHandler() {
            public String name(){return "metersphere.functional.execution."+action;}
            public String description(){return switch(action) {
                case "contract" -> "Read case steps, contentHash and policyHash for structure-only direct execution. Agent declares SUCCESS/ERROR; actual text is not compared with expected. Cover every original position; extras and repeats are allowed and retained.";
                case "get" -> "Read an authorized direct execution report with step outcomes, evidence mapping and frozen global policy. Sensitive facts are redacted.";
                case "evidence.upload" -> "Stage a PNG/JPEG evidence file as base64 (max 5 MiB), at case scope; one image covers all steps; hashes/stepNum/purpose optional. Requires FUNCTIONAL_SUBMIT and CASE_ATTACHMENT. Not visible until gate passes.";
                default -> "Validate original-step coverage and evidence structure, then publish Agent-reported results. Extra and repeated positions are retained; any ERROR makes the case ERROR. No expected-text comparison or content reviewer. ACCEPTED/PUBLISHED means structure accepted, not independently verified execution. Requires FUNCTIONAL_SUBMIT and CASE_ATTACHMENT.";
            };}
            public String requiredScope(){return read?AgentTokenScope.FUNCTIONAL_READ:AgentTokenScope.FUNCTIONAL_SUBMIT;}
            public Map<String,Object> inputSchema(){
                Map<String,Object> fields=new LinkedHashMap<>();
                fields.put("projectId",AgentMcpToolSchemas.string(1,128));fields.put("caseId",AgentMcpToolSchemas.string(1,128));
                if("get".equals(action)) fields.put("submissionId",AgentMcpToolSchemas.string(1,64));
                if(!read){fields.put("requestId",AgentMcpToolSchemas.requestId());fields.put("contentHash",AgentMcpToolSchemas.string(64,64));}
                if("evidence.upload".equals(action)) {
                    fields.put("stepNum",Map.of("type","integer","minimum",1,"maximum",1000));
                    fields.put("purpose",Map.of("type","string","enum",List.of("BEFORE_STEP","AFTER_STEP")));
                    fields.put("fileName",AgentMcpToolSchemas.string(1,200));
                    fields.put("contentType",Map.of("type","string","enum",List.of("image/png","image/jpeg")));
                    fields.put("base64",AgentMcpToolSchemas.string(1,6990508));
                }
                if("submit".equals(action)) {
                    fields.put("evidenceIds",Map.of("type","array","minItems",1,"maxItems",10,"items",AgentMcpToolSchemas.string(1,64)));
                    fields.put("policyHash",AgentMcpToolSchemas.string(64,64));
                    fields.put("steps",Map.of("type","array","minItems",1,"maxItems",1000,"items",AgentMcpToolSchemas.object(Map.of(
                            "stepNum",Map.of("type","integer","minimum",1,"maximum",1000),
                            "actual",AgentMcpToolSchemas.string(1,20000),
                            "result",Map.of("type","string","enum",List.of("SUCCESS","ERROR")),
                            "evidenceIds",Map.of("type","array","minItems",0,"maxItems",10,"items",AgentMcpToolSchemas.string(1,64))),
                            List.of("stepNum","actual","result"))));
                }
                var required=new ArrayList<>(fields.keySet());required.removeAll(List.of("contentHash","policyHash","stepNum","purpose","evidenceIds"));
                return AgentMcpToolSchemas.object(fields,required);
            }
            public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),read,!read,!read);}
            public Object execute(Map<String,Object> args){return execute.apply(args);}
        };
    }
}
