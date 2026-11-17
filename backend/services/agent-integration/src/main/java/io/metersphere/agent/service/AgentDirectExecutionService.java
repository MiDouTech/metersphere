package io.metersphere.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.quality.GlobalQualityGate;
import io.metersphere.agent.security.AgentFactCipher;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.*;

/** Personal Agent case execution. No tasks, lease credentials or Runner dependencies. */
@Service
public class AgentDirectExecutionService {
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentBusinessAccess access;
    @Resource private AgentProjectService projects;
    @Resource private AgentFunctionalCaseSearchService cases;
    @Resource private io.metersphere.functional.service.FunctionalCaseService definitions;
    @Resource private io.metersphere.agent.mapper.AgentCaseSchemaMapper schemaMapper;
    @Resource private AgentOperationExecutor operations;
    @Resource private GlobalQualityGate gate;
    @Resource private AgentFactCipher cipher;
    @Resource private AgentProtocolEvidenceService protocolEvidence;
    @Resource private AgentProtocolAccess protocolAccess;
    @org.springframework.beans.factory.annotation.Value("${agent.personal-execution.writes-enabled:true}")
    private boolean writesEnabled=true;
    public void assertWritesEnabled(){if(!writesEnabled)throw new MSException("PERSONAL_EXECUTION_WRITE_DISABLED");}
    Map<String,Object> protocolDefinition(String project,String caseId){return definition(source(Map.of("projectId",project,"caseId",caseId)));}
    Map<String,Object> protocolSource(String project,String caseId) {
        var value=source(Map.of("projectId",project,"caseId",caseId));var binding=gate.currentForSubmission();
        return Map.of("definition",definition(value),"definitionHash",hash(value),"policyHash",binding.contentHash(),"policy",parse(binding.rulesJson()));
    }

    /** Only the server-accepted protocol record can reach this adapter. No client result flag is trusted. */
    Map<String,Object> publishGovernedPersonal(Map<String,Object> session,Map<String,Object> instance,Map<String,Object> data) {
        return governedPersonal(session,instance,data,true);
    }
    void validateGovernedPersonal(Map<String,Object> session,Map<String,Object> instance,Map<String,Object> data) {
        var candidate=new LinkedHashMap<>(instance);candidate.put("verification","ACCEPTED");governedPersonal(session,candidate,data,false);
    }
    private Map<String,Object> governedPersonal(Map<String,Object> session,Map<String,Object> instance,Map<String,Object> data,boolean publish) {
        if(publish)assertWritesEnabled();
        String project=(String)session.get("project_id"),caseId=(String)instance.get("case_id"),owner=(String)session.get("owner_id");
        protocolAccess.requireSourceWrite(owner,project,(String)session.get("organization_id"),(String)session.get("environment_id"));
        jdbc.queryForList("SELECT id FROM functional_case WHERE id=? AND project_id=? FOR UPDATE",caseId,project);
        if(!"ACCEPTED".equals(instance.get("verification"))||publish&&Boolean.TRUE.equals(data.get("disputed")))fail("PROTOCOL_RESULT_NOT_ACCEPTED");
        var args=Map.<String,Object>of("projectId",project,"caseId",caseId);var current=source(args);var legacy=AgentProtocolData.object(data.get("legacy"));
        if(!hash(current).equals(legacy.get("definitionHash")))fail("VERSION_CONFLICT");
        if(!access.userHasPermission(owner,project,"FUNCTIONAL_CASE:READ+UPDATE"))throw AgentBusinessAccess.denied();
        var existing=jdbc.queryForList("SELECT source_id FROM msp_protocol_publication WHERE instance_id=? AND result_version=?",String.class,instance.get("id"),instance.get("result_version"));
        if(!existing.isEmpty())return Map.of("submissionId",existing.getFirst(),"syncStatus","SYNCED");
        var references=new TreeSet<String>();
        for(var assertion:AgentProtocolData.objects(data.get("submittedAssertions")))for(var ref:AgentProtocolData.objects(assertion.get("evidence"))) {
            if(!"PERSONAL".equals(ref.get("kind")))fail("QUALITY_CASE_EVIDENCE_INVALID");references.add((String)ref.get("id"));
        }
        if(references.isEmpty())fail("QUALITY_CASE_EVIDENCE_REQUIRED");if(references.size()>10)fail("QUALITY_CASE_EVIDENCE_INVALID");
        var files=new ArrayList<Map<String,Object>>();var artifacts=new ArrayList<AgentExecutionArtifactDTO>();
        for(String evidenceId:references) {
            var rows=jdbc.queryForList("SELECT e.id,e.token_id,e.user_id,e.protocol_instance_id,e.file_name,e.content_type,e.size_bytes,e.sha256,e.content_hash,e.payload FROM agent_case_evidence e WHERE e.id=? AND e.project_id=? AND e.case_id=? FOR UPDATE",evidenceId,project,caseId);
            if(rows.size()!=1)fail("QUALITY_CASE_EVIDENCE_INVALID");var file=rows.getFirst();
            if(!legacy.get("definitionHash").equals(file.get("content_hash"))||file.get("payload")==null)fail("QUALITY_CASE_EVIDENCE_INVALID");
            byte[] image=(byte[])file.get("payload");if(!DigestUtils.sha256Hex(image).equals(file.get("sha256")))fail("QUALITY_CASE_EVIDENCE_INVALID");decodeImage(Base64.getEncoder().encodeToString(image),(String)file.get("content_type"));
            if(file.get("protocol_instance_id")!=null) {if(!instance.get("id").equals(file.get("protocol_instance_id")))fail("QUALITY_CASE_EVIDENCE_INVALID");}
            else if(!owner.equals(file.get("user_id"))||jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_subject WHERE token_id=? AND user_id=? AND agent_ref=? AND organization_id=?",Long.class,file.get("token_id"),owner,session.get("agent_ref"),session.get("organization_id"))!=1)fail("QUALITY_CASE_EVIDENCE_INVALID");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_evidence_hold WHERE kind='PERSONAL' AND evidence_id=? AND owner_id=?",Long.class,evidenceId,instance.get("id"))!=1)fail("QUALITY_CASE_EVIDENCE_INVALID");
            var artifact=new AgentExecutionArtifactDTO();artifact.setContentType((String)file.get("content_type"));artifact.setSizeBytes(((Number)file.get("size_bytes")).longValue());artifacts.add(artifact);files.add(file);
        }
        gate.verifyDirectCaseEvidence(artifacts);var policy=gate.currentForSubmission();
        var declared=AgentProtocolData.objects(AgentProtocolData.object(data.get("contract")).get("steps"));
        var submitted=new ArrayList<Map<String,Object>>();
        for(var step:AgentProtocolData.effectiveSteps(data)) {
            var declaration=declared.stream().filter(d->step.get("stepId").equals(d.get("id"))).findFirst().orElseThrow(AgentBusinessAccess::denied);
            int position=((Number)declaration.getOrDefault("sourcePosition",declared.indexOf(declaration)+1)).intValue();
            if(!Set.of("SATISFIED","VIOLATED").contains(step.get("outcome")))fail("QUALITY_FINAL_FACTS_INCOMPLETE");
            submitted.add(Map.of("stepNum",position,"actual",step.get("observation"),"result","SATISFIED".equals(step.get("outcome"))?"SUCCESS":"ERROR"));
        }
        requireStepCoverage(MAPPER.valueToTree(submitted),current.getSteps().size());
        boolean anyFailed=submitted.stream().anyMatch(step->"ERROR".equals(step.get("result")));
        if("PASS".equals(instance.get("result"))&&anyFailed||"FAIL".equals(instance.get("result"))&&!anyFailed)fail("QUALITY_FAILURE_FACTS_MISMATCH");
        if(!publish)return Map.of("legacyGatePassed",true);
        String result="PASS".equals(instance.get("result"))?"SUCCESS":"ERROR",submission=UUID.randomUUID().toString();long now=System.currentTimeMillis();
        var facts=new LinkedHashMap<String,Object>();facts.put("schemaVersion","governed-execution.v1");facts.put("definition",definition(current));facts.put("steps",submitted);facts.put("policySnapshot",parse(policy.rulesJson()));facts.put("evidenceIds",references);facts.put("evidenceScope","CASE");facts.put("validationMode","INDEPENDENT_ASSERTION_V1");facts.put("resultSource","INDEPENDENT_HUMAN");facts.put("protocolInstanceId",instance.get("id"));facts.put("verificationRevision",instance.get("revision"));facts.put("reviewedAssertions",data.get("reviewedAssertions"));
        jdbc.update("INSERT INTO agent_case_submission(id,token_id,user_id,project_id,case_id,content_hash,policy_hash,facts,result,create_time,gate_status,review_code,reviewer_version,definition_hash_version,facts_schema_version) VALUES (?,?,?,?,?,?,?,?,?,?,'ACCEPTED','INDEPENDENT_ASSERTIONS','independent-v1','definition-v2','governed-execution.v1')",submission,files.getFirst().get("token_id"),owner,project,caseId,hash(current),policy.contentHash(),cipher.protectAll(JSON.toJSONString(facts)),result,now);
        for(var file:files) {
            // New governed holds, not an invented TTL, protect this immutable reference.
            jdbc.update("INSERT INTO agent_case_submission_evidence(submission_id,evidence_id,retain_until) VALUES (?,?,?)",submission,file.get("id"),now);
            jdbc.update("UPDATE agent_case_evidence SET submission_id=COALESCE(submission_id,?) WHERE id=?",submission,file.get("id"));
            jdbc.update("INSERT INTO functional_case_attachment(id,case_id,file_id,file_name,size,local,create_user,create_time,file_source) VALUES (?,?,?,?,?,1,?,?,'ATTACHMENT')",UUID.randomUUID().toString(),caseId,file.get("id"),file.get("file_name"),file.get("size_bytes"),owner,now);
        }
        definitions.publishPersonalExecution(project,caseId,submission,result,owner,now);
        jdbc.update("INSERT INTO msp_protocol_publication(instance_id,result_version,source_id,create_time) VALUES (?,?,?,?)",instance.get("id"),instance.get("result_version"),submission,now);
        return Map.of("submissionId",submission,"syncStatus","SYNCED");
    }
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_FILE = 5 * 1024 * 1024;
    static final int MAX_SUBMITTED_STEPS = 1000;
    private static final String VALIDATION_MODE = "STRUCTURE_ONLY_V1";

    private void authorize(Map<String,Object> args, boolean write) {
        access.caseProject(string(args,"caseId"),string(args,"projectId"),
                write ? "FUNCTIONAL_CASE:READ+UPDATE" : "FUNCTIONAL_CASE:READ",write,
                write ? AgentTokenScope.FUNCTIONAL_SUBMIT : AgentTokenScope.FUNCTIONAL_READ);
        if(write) access.caseProject(string(args,"caseId"),string(args,"projectId"),
                "FUNCTIONAL_CASE:READ+UPDATE",true,AgentTokenScope.CASE_ATTACHMENT);
    }

    @Transactional(readOnly=false)
    public Map<String,Object> contract(Map<String,Object> input) {
        var args=canonicalArguments(input);
        authorize(args,false);
        var source=source(args,false);
        var policy=gate.currentForSubmission();
        var response=new LinkedHashMap<String,Object>(Map.of("case",source,"contentHash",hash(source),"policyHash",policy.contentHash(),
                "policy",parse(policy.rulesJson()),"comparison","NONE_AGENT_REPORTED",
                "validationMode",VALIDATION_MODE,"resultSource","AGENT_REPORTED",
                "instructions","One case image is sufficient; contentHash, policyHash, stepNum/purpose binding are optional. Cover every original step by its 1-based position at least once. Extra and repeated steps are retained and allowed. Report observed actual and SUCCESS or ERROR; text is not compared with expected. Any ERROR, including repeats or extras, makes the case ERROR. Evidence rules still apply. No task, lease or content reviewer required; structure acceptance does not prove execution."));
        response.put("workflow", List.of(
                Map.of("tool", "metersphere.functional.execution.evidence.upload", "output", "evidenceId"),
                Map.of("tool", "metersphere.functional.execution.submit", "output", "submissionId"),
                Map.of("tool", "metersphere.functional.execution.get", "input", "submissionId")));
        response.put("recovery", Map.of("tool", "metersphere.operation.get", "reuseOriginalRequestId", true, "notFoundMeans", "UNKNOWN"));
        response.put("writesEnabled",writesEnabled);response.put("evidenceScope","CASE");response.put("minimumEvidenceCount",1);response.put("maximumEvidenceCount",10);
        response.put("stepEvidenceRequired",false);response.put("clientHashesRequired",false);
        response.put("effectiveEvidencePolicy",Map.of("ruleVersion","case-image-v1","scope","CASE",
                "minimumEvidenceCount",1,"maximumEvidenceCount",10,"stepEvidenceRequired",false,
                "requiredRoles",List.of(),"takesPrecedenceOverPolicySnapshot",true));
        response.put("definitionType",Objects.toString(source.getCaseEditType(),"UNKNOWN"));
        response.put("supported","STEP".equalsIgnoreCase(source.getCaseEditType()) && source.getSteps()!=null && !source.getSteps().isEmpty() && source.getSteps().size()<=100 && source.getSteps().stream().allMatch(step->step.getDesc()!=null && !step.getDesc().isBlank() && step.getExpected()!=null && !step.getExpected().isBlank()));
        response.put("limitations",Map.of("originalSteps",100,"submittedSteps",1000,"factsBytes",1048576));
        if(!Boolean.TRUE.equals(response.get("supported")))response.put("unsupportedReason","当前个人执行仅支持包含完整操作和预期的1至100步结构化用例");
        return response;
    }

    public Map<String,Object> upload(Map<String,Object> input) {
        var args=canonicalArguments(input);
        authorize(args,true);
        var replay=operations.completed("functional.execution.evidence.upload",string(args,"requestId"),args,Map.class,()->authorize(args,true));
        if(replay.isPresent()) return evidenceState(args,replay.get());
        assertWritesEnabled();
        byte[] preparedBytes=AgentMcpTiming.measure("image_decode", () -> decodeImage(string(args,"base64"),string(args,"contentType")));
        String preparedHash=DigestUtils.sha256Hex(preparedBytes);
        var response=operations.execute("functional.execution.evidence.upload",string(args,"requestId"),args,Map.class,
                () -> authorize(args,true), () -> {
                    assertWritesEnabled();
                    var source=AgentMcpTiming.measure("source_read", () -> source(args));
                    if(MAPPER.valueToTree(definition(source)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1048576)fail("STEP_FACTS_LIMIT_EXCEEDED");
                    String name=string(args,"fileName"), type=string(args,"contentType");
                    if(name.length()>200 || name.contains("/") || name.contains("\\") || name.chars().anyMatch(c->c<32 || c=='"')) fail("QUALITY_CASE_EVIDENCE_INVALID");
                    byte[] bytes=preparedBytes;
                    String suffix="image/png".equals(type)?".png":".jpg";
                    if(!(name.toLowerCase(Locale.ROOT).endsWith(suffix) || "image/jpeg".equals(type) && name.toLowerCase(Locale.ROOT).endsWith(".jpeg"))) fail("QUALITY_CASE_EVIDENCE_INVALID");
                    var token=AgentTokenContext.get();
                    // Serialize a token's pending upload quota, including concurrent requests.
                    AgentMcpTiming.measure("upload_quota_lock", () -> jdbc.queryForList("SELECT id FROM agent_token WHERE id=? FOR UPDATE",token.getId()));
                    var quota=AgentMcpTiming.measure("upload_quota_sum", () -> jdbc.queryForMap("SELECT COALESCE(SUM(CASE WHEN expires_at>? THEN size_bytes ELSE 0 END),0) AS active_bytes, COALESCE(SUM(size_bytes),0) AS physical_bytes FROM agent_case_evidence WHERE token_id=? AND submission_id IS NULL",System.currentTimeMillis(),token.getId()));
                    if(((Number)quota.get("active_bytes")).longValue()+bytes.length>50L*1024*1024
                            || ((Number)quota.get("physical_bytes")).longValue()+bytes.length>100L*1024*1024) fail("STEP_FACTS_LIMIT_EXCEEDED");
                    String id=UUID.randomUUID().toString(); long now=System.currentTimeMillis();
                    jdbc.update("INSERT INTO agent_case_evidence(id,token_id,user_id,project_id,case_id,content_hash,step_num,purpose,file_name,content_type,size_bytes,sha256,payload,create_time,expires_at,evidence_scope,definition_hash_version,definition_snapshot) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            id,token.getId(),token.getUserId(),args.get("projectId"),args.get("caseId"),hash(source),null,null,name,type,bytes.length,preparedHash,bytes,now,now+86400000L,"CASE","definition-v2",cipher.protect(MAPPER.valueToTree(definition(source)).toString()));
                    return Map.of("evidenceId",id,"sha256",preparedHash,"sizeBytes",bytes.length,"expiresAt",now+86400000L,"evidenceScope","CASE","caseId",args.get("caseId"),"projectId",args.get("projectId"));
                });
        return evidenceState(args,response);
    }

    public Map<String,Object> evidenceState(Map<String,Object> input,Map<String,Object> response) {
        var args=canonicalArguments(input);
        authorize(args,true);
        var result=new LinkedHashMap<String,Object>(response);
        var files=jdbc.queryForList("SELECT submission_id,expires_at FROM agent_case_evidence WHERE id=? AND token_id=? AND case_id=? AND project_id=?",response.get("evidenceId"),AgentTokenContext.get().getId(),args.get("caseId"),args.get("projectId"));
        result.put("resourceState",files.isEmpty()?"EXPIRED":files.getFirst().get("submission_id")!=null?"CONSUMED":((Number)files.getFirst().get("expires_at")).longValue()<=System.currentTimeMillis()?"EXPIRED":"STAGED");
        return result;
    }

    public Map<String,Object> report(Map<String,Object> input) {
        var args=canonicalArguments(input);
        authorize(args,false);
        var rows=jdbc.queryForList("SELECT id,result,gate_status,review_code,reviewer_version,content_hash,policy_hash,facts,create_time FROM agent_case_submission WHERE id=? AND case_id=? AND project_id=?",
                string(args,"submissionId"),string(args,"caseId"),string(args,"projectId"));
        if(rows.size()!=1) throw AgentBusinessAccess.denied();
        var result=new LinkedHashMap<>(rows.getFirst());
        result.put("facts",parse(io.metersphere.agent.security.AgentSensitiveDataSanitizer.sanitizeJson(cipher.reveal((String)result.get("facts")))));
        // Keep legacy fields readable while exposing the same status vocabulary as submit.
        String gateStatus = (String) result.get("gate_status");
        result.put("submissionId", result.get("id"));
        result.put("caseId", args.get("caseId"));
        result.put("gateStatus", gateStatus);
        result.put("publishStatus", "ACCEPTED".equals(gateStatus) ? "PUBLISHED" : "NEEDS_REVIEW".equals(gateStatus) ? "NOT_PUBLISHED" : "UNKNOWN");
        result.put("policyHash", result.get("policy_hash"));
        result.put("contentHash", result.get("content_hash"));
        result.put("reviewerVersion", result.get("reviewer_version"));
        JsonNode facts = (JsonNode)result.get("facts");
        result.put("validationMode", facts.path("validationMode").asText("LEGACY"));
        result.put("resultSource", facts.path("resultSource").asText("LEGACY"));
        result.put("evidenceScope",facts.path("evidenceScope").asText("STEP"));
        result.put("evidenceIds",facts.path("evidenceIds"));
        result.put("issues", "NEEDS_REVIEW".equals(gateStatus)
                ? List.of(Map.of("ruleId", "Q-EVIDENCE-CONTENT", "code", Objects.toString(result.get("review_code"), "EVIDENCE_REVIEW_UNAVAILABLE"),
                    "message", "证据内容尚未通过独立核验；正式附件和执行结果未更新"))
                : List.of());
        return result;
    }

    public Map<String,Object> submit(Map<String,Object> input) {
        var args=canonicalArguments(input);
        authorize(args,true);
        var replay=operations.completed("functional.execution.submit",string(args,"requestId"),args,Map.class,()->authorize(args,true));
        if(replay.isPresent()) return replay.get();
        return operations.execute("functional.execution.submit",string(args,"requestId"),args,Map.class,
                () -> authorize(args,true), () -> {
                    assertWritesEnabled();
                    // Same case row as normal editing; no stale source may be published.
                    jdbc.queryForList("SELECT id FROM functional_case WHERE id=? FOR UPDATE",args.get("caseId"));
                    var source=AgentMcpTiming.measure("source_read", () -> source(args));
                    String sourceHash=hash(source);

                    JsonNode steps=MAPPER.valueToTree(args.get("steps"));
                    requireStepCoverage(steps,source.getSteps().size());
                    var token=AgentTokenContext.get(); long now=System.currentTimeMillis();
                    Set<String> evidenceIds=collectEvidence(args,steps);
                    List<Map<String,Object>> evidence=new ArrayList<>(); boolean failed=false;
                    long totalBytes=0;
                    var orderedIds=new ArrayList<>(new TreeSet<>(evidenceIds));
                    var parameters=new ArrayList<Object>(orderedIds);
                    parameters.addAll(List.of(token.getId(),token.getUserId(),args.get("projectId"),args.get("caseId"),now));
                    var evidenceRows=AgentMcpTiming.measure("evidence_lock_read", () -> jdbc.queryForList("SELECT id,file_name,content_type,size_bytes,sha256,evidence_scope,definition_hash_version,content_hash FROM agent_case_evidence WHERE id IN ("+String.join(",",Collections.nCopies(orderedIds.size(),"?"))+") AND token_id=? AND user_id=? AND project_id=? AND case_id=? AND submission_id IS NULL AND expires_at>? ORDER BY id FOR UPDATE",parameters.toArray()));
                    if(evidenceRows.size()!=orderedIds.size()) fail("QUALITY_CASE_EVIDENCE_INVALID");
                    for(var file:evidenceRows) {
                        String algorithm=Objects.toString(file.get("definition_hash_version"),"legacy-dto-v1");
                        if("definition-v2".equals(algorithm)) {
                            if(!sourceHash.equals(file.get("content_hash"))) fail("VERSION_CONFLICT");
                        } else if("legacy-dto-v1".equals(algorithm)) {
                            var legacy=cases.getById(string(args,"caseId"),true,null);
                            legacy.getSteps().forEach(step->{step.setActualResult(null);step.setExecuteResult(null);});
                            legacy.setLastExecuteResult(null);legacy.setLastExecuteUser(null);legacy.setLastExecuteUserName(null);
                            if(!operations.hash(MAPPER.convertValue(legacy,Map.class)).equals(file.get("content_hash"))) fail("VERSION_CONFLICT");
                        } else fail("VERSION_CONFLICT");
                        totalBytes+=((Number)file.get("size_bytes")).longValue();
                        if(totalBytes>50L*1024*1024) fail("STEP_FACTS_LIMIT_EXCEEDED");
                        evidence.add(file);
                    }
                    for(JsonNode step:steps) {
                        if(!step.path("actual").isTextual() || step.path("actual").asText().isBlank() || step.path("actual").asText().length()>20000) fail("QUALITY_ASSERTION_INVALID");
                        if(!step.path("result").isTextual() || !Set.of("SUCCESS","ERROR").contains(step.path("result").asText())) fail("QUALITY_ASSERTION_INVALID");
                        failed|="ERROR".equals(step.path("result").asText());
                    }
                    var policy=gate.currentForSubmission();
                    List<AgentExecutionArtifactDTO> artifacts=new ArrayList<>();
                    for(var file:evidence) {
                        var artifact=new AgentExecutionArtifactDTO(); artifact.setContentType((String)file.get("content_type"));
                        artifact.setSizeBytes(((Number)file.get("size_bytes")).longValue()); artifacts.add(artifact);
                    }
                    gate.verifyDirectCaseEvidence(artifacts);
                    String id=UUID.randomUUID().toString(), result=failed?"ERROR":"SUCCESS";
                    var facts=new LinkedHashMap<String,Object>();
                    facts.put("schemaVersion","direct-execution.v2");facts.put("definition",definition(source));facts.put("steps",steps);
                    facts.put("policySnapshot",parse(policy.rulesJson()));facts.put("evidence",evidence);facts.put("evidenceIds",evidenceIds);
                    facts.put("evidenceScope","CASE");facts.put("validationMode",VALIDATION_MODE);facts.put("resultSource","AGENT_REPORTED");
                    facts.put("directEvidenceRuleVersion","case-image-v1");
                    String encodedFacts=JSON.toJSONString(facts);
                    if(encodedFacts.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1024*1024)
                        fail("STEP_FACTS_LIMIT_EXCEEDED");
                    jdbc.update("INSERT INTO agent_case_submission(id,token_id,user_id,project_id,case_id,content_hash,policy_hash,facts,result,create_time,gate_status,review_code,reviewer_version,definition_hash_version,facts_schema_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            id,token.getId(),token.getUserId(),args.get("projectId"),args.get("caseId"),sourceHash,policy.contentHash(),cipher.protect(encodedFacts),result,now,"ACCEPTED","STRUCTURE_VALIDATED","structure-v1","definition-v2","direct-execution.v2");
                    jdbc.batchUpdate("INSERT INTO agent_case_submission_evidence(submission_id,evidence_id,retain_until) VALUES (?,?,?)",
                            orderedIds.stream().map(evidenceId -> new Object[]{id,evidenceId,now+30L*86400000}).toList());
                    var publishedAttachments=new ArrayList<Map<String,Object>>();
                    var attachmentRows=new ArrayList<Object[]>();
                    var consumedRows=new ArrayList<Object[]>();
                    for(var file:evidence) {
                        String association=UUID.randomUUID().toString();
                        publishedAttachments.add(Map.of("evidenceId",file.get("id"),"fileId",file.get("id"),"associationId",association,"fileName",file.get("file_name"),"contentType",file.get("content_type"),"sizeBytes",file.get("size_bytes"),"sha256",file.get("sha256"),"submissionId",id));
                        consumedRows.add(new Object[]{id,file.get("id")});
                        attachmentRows.add(new Object[]{association,args.get("caseId"),file.get("id"),file.get("file_name"),file.get("size_bytes"),token.getUserId(),now});
                    }
                    jdbc.batchUpdate("UPDATE agent_case_evidence SET submission_id=? WHERE id=?",consumedRows);
                    jdbc.batchUpdate("INSERT INTO functional_case_attachment(id,case_id,file_id,file_name,size,local,create_user,create_time,file_source) VALUES (?,?,?,?,?,1,?,?, 'ATTACHMENT')",attachmentRows);
                    AgentMcpTiming.measure("publish_case_and_plans", () -> {
                        definitions.publishPersonalExecution(string(args,"projectId"),string(args,"caseId"),id,result,token.getUserId(),now);
                        return null;
                    });
                    var response=new LinkedHashMap<String,Object>(Map.of("submissionId",id,"caseId",args.get("caseId"),"result",result,"gateStatus","ACCEPTED","publishStatus","PUBLISHED","attachmentIds",evidenceIds,"policyHash",policy.contentHash(),"validationMode",VALIDATION_MODE,"resultSource","AGENT_REPORTED"));
                    response.put("evidenceIds",evidenceIds);response.put("evidenceScope","CASE");response.put("attachments",publishedAttachments);return response;
                });
    }

    static void requireStepCoverage(JsonNode steps, int requiredCount) {
        if(steps==null || !steps.isArray() || steps.isEmpty() || steps.size()>MAX_SUBMITTED_STEPS)
            fail("QUALITY_FINAL_FACTS_INCOMPLETE");
        Set<Integer> covered=new HashSet<>();
        for(JsonNode step:steps) {
            JsonNode number=step.path("stepNum");
            if(!step.isObject() || !number.isIntegralNumber() || !number.canConvertToInt()
                    || number.intValue()<1 || number.intValue()>MAX_SUBMITTED_STEPS)
                fail("QUALITY_FINAL_FACTS_INCOMPLETE");
            if(number.intValue()<=requiredCount) covered.add(number.intValue());
        }
        if(covered.size()!=requiredCount) fail("QUALITY_FINAL_FACTS_INCOMPLETE");
    }

    Map<String,Object> canonicalArguments(Map<String,Object> input) {
        var args=new LinkedHashMap<>(input);
        args.put("projectId",projects.resolveProjectId(string(input,"projectId")));
        return args;
    }

    private AgentCaseDTO source(Map<String,Object> args) {return source(args,true);}
    private AgentCaseDTO source(Map<String,Object> args,boolean executable) {
        String project=string(args,"projectId"),id=string(args,"caseId");
        var detail=definitions.getAgentCaseDetails(List.of(id),project,true).get(id);
        if(detail==null || !project.equals(detail.getProjectId())) throw AgentBusinessAccess.denied();
        var source=new AgentCaseDTO();
        source.setCaseId(id);source.setProjectId(project);source.setName(detail.getName());source.setVersionId(detail.getVersionId());
        schemaMapper.enrichDetail(source,detail,new ArrayList<>());
        source.setExecutionRef(null);source.setExecutionFacts(null);source.setLastExecuteResult(null);
        source.setLastExecuteUser(null);source.setLastExecuteUserName(null);source.setUpdateTime(null);
        source.setReviewStatus(null);source.setModuleId(null);source.setModulePath(null);
        if(source.getCustomFields()!=null)source.setCustomFields(source.getCustomFields().stream().filter(field->!Boolean.TRUE.equals(field.getInternal())).sorted(java.util.Comparator.comparing(io.metersphere.system.dto.sdk.TemplateCustomFieldDTO::getFieldId)).map(field->{
            var value=new io.metersphere.system.dto.sdk.TemplateCustomFieldDTO();value.setFieldId(field.getFieldId());value.setDefaultValue(field.getDefaultValue());return value;
        }).toList());
        if(source.getSteps()!=null)source.getSteps().forEach(step->{step.setActualResult(null);step.setExecuteResult(null);});
        if(!executable)return source;
        if(!"STEP".equalsIgnoreCase(source.getCaseEditType())) fail("CASE_CONTENT_MISSING");
        if(source.getSteps()==null || source.getSteps().isEmpty() || source.getSteps().size()>100) fail("CASE_CONTENT_MISSING");
        for(var step:source.getSteps()) {
            if(step.getDesc()==null || step.getDesc().isBlank() || step.getExpected()==null || step.getExpected().isBlank()) fail("CASE_CONTENT_MISSING");
            step.setActualResult(null);step.setExecuteResult(null);
        }
        return source;
    }
    static Set<String> collectEvidence(Map<String,Object> args,JsonNode steps) {
        Set<String> ids=new LinkedHashSet<>();
        if(args.containsKey("evidenceIds")) collectIds(MAPPER.valueToTree(args.get("evidenceIds")),ids);
        for(JsonNode step:steps) if(step.has("evidenceIds")) collectIds(step.get("evidenceIds"),ids);
        if(ids.isEmpty()) fail("QUALITY_CASE_EVIDENCE_REQUIRED");
        if(ids.size()>1000) fail("STEP_FACTS_LIMIT_EXCEEDED");
        return ids;
    }
    private static void collectIds(JsonNode values,Set<String> ids) {
        if(!values.isArray() || values.size()>10) fail("QUALITY_CASE_EVIDENCE_INVALID");
        for(JsonNode value:values) {
            if(!value.isTextual() || value.asText().isBlank() || value.asText().length()>64) fail("QUALITY_CASE_EVIDENCE_INVALID");
            ids.add(value.asText());
        }
    }
    private String hash(AgentCaseDTO source) {return operations.hash(definition(source));}
    /** Versioned whitelist: additions to query DTOs cannot change a definition-v2 fingerprint. */
    static Map<String,Object> definition(AgentCaseDTO source) {
        var result=new LinkedHashMap<String,Object>();
        result.put("caseId",source.getCaseId());result.put("projectId",source.getProjectId());
        result.put("versionId",source.getVersionId());result.put("name",source.getName());
        result.put("caseEditType",source.getCaseEditType());result.put("description",source.getDescription());
        result.put("prerequisite",source.getPrerequisite());result.put("priority",source.getPriority());
        var fields=new ArrayList<Map<String,Object>>();
        if(source.getCustomFields()!=null)for(var field:source.getCustomFields()) {
            var value=new LinkedHashMap<String,Object>();value.put("fieldId",field.getFieldId());value.put("value",field.getDefaultValue());fields.add(value);
        }
        result.put("customFields",fields);
        var steps=new ArrayList<Map<String,Object>>();
        if(source.getSteps()!=null)for(int i=0;i<source.getSteps().size();i++) {
            var step=source.getSteps().get(i);var value=new LinkedHashMap<String,Object>();
            value.put("position",i+1);value.put("description",step.getDesc());value.put("expected",step.getExpected());steps.add(value);
        }
        result.put("steps",steps);return result;
    }
    static byte[] decodeImage(String base64,String type) {
        if(base64.length()>((MAX_FILE+2)/3)*4 || !Set.of("image/png","image/jpeg").contains(type)) throw new MSException("QUALITY_CASE_EVIDENCE_INVALID");
        try {
            byte[] bytes=Base64.getDecoder().decode(base64);
            if(bytes.length==0 || bytes.length>MAX_FILE) throw new IllegalArgumentException();
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw new IllegalArgumentException();
                var reader=readers.next();
                try {
                    reader.setInput(input);
                    String format=reader.getFormatName();
                    if(!("image/png".equals(type)?"png":"JPEG").equalsIgnoreCase(format)
                            || (long)reader.getWidth(0)*reader.getHeight(0)>20_000_000L) throw new IllegalArgumentException();
                    if(reader.getWidth(0)<64 || reader.getHeight(0)<64 || reader.read(0)==null) throw new IllegalArgumentException();
                } finally {reader.dispose();}
            }
            return bytes;
        } catch(Exception error) { throw new MSException("QUALITY_CASE_EVIDENCE_INVALID"); }
    }
    @Scheduled(fixedDelay=3600000)
    public void expirePendingEvidence() {
        long now=System.currentTimeMillis();
        protocolEvidence.cleanupPersonal(now);
    }
    private static String string(Map<String,Object> args,String key) {
        if(!(args.get(key) instanceof String value) || value.isBlank()) throw new MSException("AGENT_VALIDATION_ERROR");
        return (String)args.get(key);
    }
    private static int number(Map<String,Object> args,String key) {
        if(!(args.get(key) instanceof Number value) || value.doubleValue()!=value.intValue()) throw new MSException("AGENT_VALIDATION_ERROR");
        return ((Number)args.get(key)).intValue();
    }
    private static JsonNode parse(String json) {
        try{return MAPPER.readTree(json);}catch(Exception error){throw new MSException("QUALITY_CONTRACT_INVALID");}
    }
    private static void fail(String code) {throw new MSException(code);}
}
