package io.metersphere.agent.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

@Service
public class AgentStructuredContractService {
    private final JdbcTemplate jdbc;private final AgentProtocolStore store;private final AgentProtocolAccess access;
    private final AgentSessionProtocolConfiguration config;
    @jakarta.annotation.Resource private AgentDirectExecutionService direct;
    @jakarta.annotation.Resource private AgentKnowledgeService knowledge;
    public AgentStructuredContractService(JdbcTemplate jdbc,AgentProtocolStore store,AgentProtocolAccess access,AgentSessionProtocolConfiguration config){this.jdbc=jdbc;this.store=store;this.access=access;this.config=config;}
    public Map<String,Object> get(String project,String caseId,Long version) {
        var actor=access.sessionActor(access.organizationForProject(project),false);access.project(actor,project,false);access.caseIdentity(project,caseId);
        var rows=jdbc.queryForList("SELECT * FROM msp_case_contract WHERE project_id=? AND case_id=?"+(version==null?" ORDER BY version DESC LIMIT 1":" AND version=?"),version==null?new Object[]{project,caseId}:new Object[]{project,caseId,version});
        if(rows.size()!=1)throw io.metersphere.agent.service.AgentBusinessAccess.denied();
        var result=store.data(rows.getFirst());result.put("contractVersion",rows.getFirst().get("version"));result.put("state",rows.getFirst().get("state"));return result;
    }
    public Map<String,Object> conversion(String project,String caseId) {
        var actor=access.sessionActor(access.organizationForProject(project),false);access.project(actor,project,false);access.caseIdentity(project,caseId);
        var source=direct.protocolDefinition(project,caseId);var steps=new ArrayList<Map<String,Object>>();var assertions=new ArrayList<Map<String,Object>>();
        int position=0;for(var original:objects(source.get("steps"))) {
            position++;String step="S"+position;
            steps.add(Map.of("id",step,"sourcePosition",position,"action",original.get("description"),"expectedObservation",original.get("expected"),"inputBindings",Map.of()));
            assertions.add(Map.of("id","A"+position,"required",true,"stepId",step,"entryPoint","","expected",original.get("expected"),"evidenceTypes",List.of("IMAGE"),"objectFields",List.of(),"verificationMethod","INDEPENDENT_HUMAN","failureRule",""));
        }
        var result=new LinkedHashMap<String,Object>();result.put("schemaVersion","1.0");result.put("goal",source.get("name"));result.put("preconditions",Objects.toString(source.get("prerequisite"),""));result.put("environmentId","");result.put("riskBoundary","");result.put("steps",steps);result.put("assertions",assertions);result.put("outputs",Map.of());result.put("dependencies",List.of());result.put("knowledgeRefs",List.of());result.put("dataRequirements",List.of());result.put("allowedActions",List.of());return result;
    }
    public Map<String,Object> published(String project,String caseId,long version) {
        var rows=jdbc.queryForList("SELECT * FROM msp_case_contract WHERE project_id=? AND case_id=? AND version=? AND state='PUBLISHED'",project,caseId,version);
        require(rows.size()==1,"PROTOCOL_CONTRACT_UNAVAILABLE");var row=rows.getFirst();
        var result=store.data(row);result.put("contractVersion",version);result.put("authorPerson",row.get("author_person"));result.put("authorUserId",row.get("author_id"));result.put("contentHash",row.get("content_hash"));result.put("sourceVersion",row.get("source_version"));return result;
    }
    public Map<String,Object> save(String project,String caseId,Map<String,Object> args) {
        fields(args,"expectedVersion","content","publish");String org=access.organizationForProject(project);
        return store.execute("contract.save",caseId,args,lock->{var actor=access.sessionActor(org,lock);access.project(actor,project,false);return actor;},()->{},ctx->{
            access.project(ctx.actor(),project,true);access.caseIdentity(project,caseId);config.requireKnowledge();
            require(ctx.actor().human(),"PROTOCOL_HUMAN_REQUIRED");require(ctx.actor().person()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");
            var source=jdbc.queryForList("SELECT version_id FROM functional_case WHERE id=? AND project_id=? AND deleted=0 FOR UPDATE",caseId,project);
            require(source.size()==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
            Long current=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM msp_case_contract WHERE case_id=?",Long.class,caseId);
            require(number(args,"expectedVersion")==current,"VERSION_CONFLICT");
            var content=object(args.get("content"));List<String> issues=validate(project,caseId,content,false);
            boolean publish=Boolean.TRUE.equals(args.get("publish"));if(publish)require(issues.isEmpty(),"PROTOCOL_CONTRACT_INCOMPLETE");
            long version=current+1;
            jdbc.update("INSERT INTO msp_case_contract(case_id,version,project_id,state,author_id,author_person,source_version,content_hash,payload,create_time) VALUES (?,?,?,?,?,?,?,?,?,?)",caseId,version,project,publish?"PUBLISHED":"DRAFT",ctx.actor().user(),ctx.actor().person(),Objects.toString(source.getFirst().get("version_id"),caseId),hash(content),store.payload(content),System.currentTimeMillis());
            store.event("CASE:"+caseId,null,ctx.operationId(),publish?"CONTRACT_PUBLISHED":"CONTRACT_DRAFT",ctx.actor().user(),Map.of("version",version,"issues",issues));
            return Map.of("caseId",caseId,"version",version,"state",publish?"PUBLISHED":"DRAFT","issues",issues);
        });
    }
    public List<String> validate(String project,String caseId,Map<String,Object> value,boolean snapshot) {
        fields(value,"schemaVersion","goal","preconditions","dataRequirements","steps","assertions","outputs","dependencies","knowledgeRefs","environmentId","allowedActions","riskBoundary","variables");
        AgentProtocolTypes.definition(object(value.getOrDefault("outputs",Map.of())));AgentProtocolTypes.definition(object(value.getOrDefault("variables",Map.of())));
        require("1.0".equals(value.get("schemaVersion")),"PROTOCOL_VERSION_UNSUPPORTED");
        limited(encode(value),Long.MAX_VALUE,config.number("limits.content-utf8-bytes"));
        List<String> issues=new ArrayList<>();for(String key:List.of("goal","preconditions","steps","assertions","environmentId","riskBoundary"))if(!value.containsKey(key)||value.get(key)==null||value.get(key).toString().isBlank())issues.add("MISSING_"+key.toUpperCase(Locale.ROOT));
        Set<String> stepIds=new HashSet<>(),assertionIds=new HashSet<>(),dataKeys=new HashSet<>(),aliases=new HashSet<>();Set<Long> positions=new HashSet<>();
        for(var step:objects(value.getOrDefault("steps",List.of()))) {
            fields(step,"id","sourcePosition","action","expectedObservation","inputBindings");require(stepIds.add(id(step,"id")),"PROTOCOL_CONTRACT_INVALID");require(number(step,"sourcePosition")>0&&positions.add(number(step,"sourcePosition")),"PROTOCOL_CONTRACT_INVALID");
            if(!(step.get("action") instanceof String action)||action.isBlank()||!(step.get("expectedObservation") instanceof String expected)||expected.isBlank())issues.add("STEP_INCOMPLETE");
        }
        for(var assertion:objects(value.getOrDefault("assertions",List.of()))) {
            fields(assertion,"id","required","entryPoint","expected","evidenceTypes","objectFields","verificationMethod","failureRule","stepId");
            require(assertionIds.add(id(assertion,"id"))&&assertion.get("required") instanceof Boolean,"PROTOCOL_CONTRACT_INVALID");
            for(String key:List.of("entryPoint","expected","evidenceTypes","objectFields","verificationMethod","failureRule","stepId"))if(!assertion.containsKey(key)||assertion.get(key)==null||(assertion.get(key) instanceof String text&&text.isBlank()))issues.add("ASSERTION_INCOMPLETE");
            for(String key:List.of("evidenceTypes","objectFields"))if(assertion.containsKey(key)&&strings(assertion.get(key)).stream().noneMatch(item->!item.isBlank()))issues.add("ASSERTION_INCOMPLETE");
            if(assertion.containsKey("verificationMethod"))require(Set.of("INDEPENDENT_HUMAN","AUTHORIZED_QUERY").contains(assertion.get("verificationMethod")),"PROTOCOL_CONTRACT_INVALID");
            if(assertion.containsKey("stepId")&&!stepIds.contains(assertion.get("stepId")))issues.add("ASSERTION_STEP_MISSING");
        }
        if(objects(value.getOrDefault("assertions",List.of())).stream().noneMatch(item->Boolean.TRUE.equals(item.get("required"))))issues.add("NO_REQUIRED_ASSERTION");
        for(var variable:object(value.getOrDefault("variables",Map.of())).values())if(!object(variable).containsKey("scope"))issues.add("VARIABLE_SCOPE_UNDECLARED");
        if(assertionIds.isEmpty()||stepIds.isEmpty())issues.add("EMPTY_CASE");
        for(var data:objects(value.getOrDefault("dataRequirements",List.of()))) {
            fields(data,"key","entity","conditions","strategies","preparationMethodRef","fields");
            require(id(data,"key").matches("[A-Za-z][A-Za-z0-9_]*")&&dataKeys.add(id(data,"key")),"PROTOCOL_CONTRACT_INVALID");
            for(String key:List.of("entity","conditions","strategies","preparationMethodRef","fields"))if(!data.containsKey(key))issues.add("DATA_REQUIREMENT_INCOMPLETE");
            if(data.containsKey("strategies")){require(Set.of("SEARCH","CREATE").containsAll(strings(data.get("strategies"))),"PROTOCOL_CONTRACT_INVALID");if(strings(data.get("strategies")).isEmpty())issues.add("DATA_REQUIREMENT_INCOMPLETE");}
            if(data.containsKey("fields")){AgentProtocolTypes.definition(object(data.get("fields")));if(data.containsKey("conditions"))AgentProtocolTypes.values(object(data.get("fields")),object(data.get("conditions")),false);}
            if(!(data.get("preparationMethodRef") instanceof String ref)||ref.isBlank())issues.add("PREPARATION_METHOD_UNAVAILABLE");
            if(!(data.get("entity") instanceof String entity)||entity.isBlank())issues.add("DATA_REQUIREMENT_INCOMPLETE");
            if(data.get("preparationMethodRef") instanceof String reference){limited(reference,config.number("limits.reference-scalars"),config.number("limits.content-utf8-bytes"));int separator=reference.lastIndexOf(":v");if(separator<1||!reference.substring(separator+2).matches("[0-9]{1,18}"))issues.add("PREPARATION_METHOD_UNAVAILABLE");else try{var actor=access.actor(access.organizationForProject(project),false);var method=knowledge.fixed(actor.organization(),reference.substring(0,separator),Long.parseLong(reference.substring(separator+2)),actor,true);require("DATA_PREPARATION".equals(method.get("type")),"PROTOCOL_KNOWLEDGE_INCOMPLETE");}catch(io.metersphere.sdk.exception.MSException invalid){if("AGENT_RESOURCE_NOT_ACCESSIBLE".equals(invalid.getMessage())&&jdbc.queryForObject("SELECT COUNT(*) FROM msp_knowledge WHERE id=?",Long.class,reference.substring(0,separator))>0)throw invalid;issues.add("PREPARATION_METHOD_UNAVAILABLE");}}
        }
        for(var dependency:objects(value.getOrDefault("dependencies",List.of()))) {
            fields(dependency,"alias","caseId","contractVersion","bindings");String producer=id(dependency,"caseId");
            require(id(dependency,"alias").matches("[A-Za-z][A-Za-z0-9_]*")&&aliases.add(id(dependency,"alias")),"PROTOCOL_CONTRACT_INVALID");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM functional_case WHERE id=? AND project_id=? AND deleted=0",Long.class,producer,project)!=1){require(jdbc.queryForObject("SELECT COUNT(*) FROM functional_case WHERE id=?",Long.class,producer)==0,"AGENT_RESOURCE_NOT_ACCESSIBLE");issues.add("DEPENDENCY_UNAVAILABLE");}
            if(producer.equals(caseId)||cycle(caseId,producer,new HashSet<>()))issues.add("DEPENDENCY_CYCLE");
            var target=jdbc.queryForList("SELECT payload FROM msp_case_contract WHERE case_id=? AND version=? AND state='PUBLISHED'",producer,number(dependency,"contractVersion"));
            if(target.isEmpty())issues.add("DEPENDENCY_UNAVAILABLE");
            else for(var binding:object(dependency.getOrDefault("bindings",Map.of())).entrySet()){
                var producerOutputs=object(store.data(target.getFirst()).getOrDefault("outputs",Map.of()));var consumerVariables=object(value.getOrDefault("variables",Map.of()));
                if(!(binding.getValue() instanceof String output)||!producerOutputs.containsKey(output)||!consumerVariables.containsKey(binding.getKey()))issues.add("DEPENDENCY_OUTPUT_UNDECLARED");
                else if(!"CASE".equals(object(consumerVariables.get(binding.getKey())).get("scope")))issues.add("DEPENDENCY_VARIABLE_SCOPE_INVALID");
                else if(!AgentProtocolTypes.compatible(object(producerOutputs.get(output)),object(consumerVariables.get(binding.getKey()))))issues.add("DEPENDENCY_TYPE_INCOMPATIBLE");
            }
        }
        for(var reference:objects(value.getOrDefault("knowledgeRefs",List.of()))){fields(reference,"knowledgeId","version");limited(id(reference,"knowledgeId"),config.number("limits.reference-scalars"),config.number("limits.content-utf8-bytes"));try{var actor=access.actor(access.organizationForProject(project),false);knowledge.fixed(actor.organization(),id(reference,"knowledgeId"),number(reference,"version"),actor,true);}catch(io.metersphere.sdk.exception.MSException missing){if("AGENT_RESOURCE_NOT_ACCESSIBLE".equals(missing.getMessage())&&jdbc.queryForObject("SELECT COUNT(*) FROM msp_knowledge WHERE id=?",Long.class,reference.get("knowledgeId"))>0)throw missing;issues.add("KNOWLEDGE_UNAVAILABLE");}}
        inspectBindings(value,issues,dataKeys);
        var dependencies=objects(value.getOrDefault("dependencies",List.of()));var variables=object(value.getOrDefault("variables",Map.of()));
        for(var step:objects(value.getOrDefault("steps",List.of())))for(Object binding:object(step.getOrDefault("inputBindings",Map.of())).values())if(binding instanceof String reference){
            String[] parts=reference.split("\\.");if(parts.length<2)continue;
            if("execution".equals(parts[0])&&(!variables.containsKey(parts[1])||!"EXECUTION".equals(object(variables.get(parts[1])).get("scope"))))issues.add("VARIABLE_UNDECLARED");
            if("case".equals(parts[0])&&parts.length==4){var dependency=dependencies.stream().filter(d->parts[1].equals(d.get("alias"))).findFirst();if(dependency.isEmpty())issues.add("DEPENDENCY_UNDECLARED");else{
                var producer=jdbc.queryForList("SELECT payload FROM msp_case_contract WHERE case_id=? AND version=? AND state='PUBLISHED'",dependency.get().get("caseId"),number(dependency.get(),"contractVersion"));
                if(producer.isEmpty()||!object(store.data(producer.getFirst()).getOrDefault("outputs",Map.of())).containsKey(parts[3]))issues.add("DEPENDENCY_OUTPUT_UNDECLARED");
            }}
        }
        if(snapshot)require(issues.isEmpty(),"PROTOCOL_CONTRACT_INCOMPLETE");return issues.stream().distinct().toList();
    }
    private boolean cycle(String root,String next,Set<String> path) {
        if(root.equals(next)||!path.add(next))return true;
        var rows=jdbc.queryForList("SELECT payload FROM msp_case_contract WHERE case_id=? AND state='PUBLISHED' ORDER BY version DESC LIMIT 1",next);
        if(rows.isEmpty())return false;
        for(var dependency:objects(store.data(rows.getFirst()).getOrDefault("dependencies",List.of())))if(cycle(root,id(dependency,"caseId"),new HashSet<>(path)))return true;return false;
    }
    private void inspectBindings(Object value,List<String> issues,Set<String> dataKeys) {
        if(value instanceof Map<?,?> map){for(var entry:map.entrySet()) {
            if("inputBindings".equals(entry.getKey()))for(Object binding:object(entry.getValue()).values()) {
                if(binding instanceof String reference)limited(reference,config.number("limits.reference-scalars"),config.number("limits.content-utf8-bytes"));
                if(!(binding instanceof String text)||!text.matches("(?:data\\.[A-Za-z][A-Za-z0-9_]*\\.[A-Za-z][A-Za-z0-9_]*|case\\.[A-Za-z][A-Za-z0-9_]*\\.outputs\\.[A-Za-z][A-Za-z0-9_]*|execution\\.[A-Za-z][A-Za-z0-9_]*)"))issues.add("VARIABLE_SYNTAX_INVALID");
                else if(text.startsWith("data.")&&!dataKeys.contains(text.split("\\.")[1]))issues.add("VARIABLE_UNDECLARED");
            } else inspectBindings(entry.getValue(),issues,dataKeys);
        }}else if(value instanceof List<?> list)list.forEach(item->inspectBindings(item,issues,dataKeys));
    }
}
