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
    @Resource private AgentFunctionalCaseSearchService cases;
    @Resource private AgentOperationExecutor operations;
    @Resource private GlobalQualityGate gate;
    @Resource private AgentFactCipher cipher;
    @Resource private io.metersphere.agent.quality.EvidenceContentReviewer evidenceReviewer;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_FILE = 5 * 1024 * 1024;

    private void authorize(Map<String,Object> args, boolean write) {
        access.caseProject(string(args,"caseId"),string(args,"projectId"),
                write ? "FUNCTIONAL_CASE:READ+UPDATE" : "FUNCTIONAL_CASE:READ",write,
                write ? AgentTokenScope.FUNCTIONAL_SUBMIT : AgentTokenScope.FUNCTIONAL_READ);
        if(write) access.caseProject(string(args,"caseId"),string(args,"projectId"),
                "FUNCTIONAL_CASE:READ+UPDATE",true,AgentTokenScope.CASE_ATTACHMENT);
    }

    @Transactional(readOnly=false)
    public Map<String,Object> contract(Map<String,Object> args) {
        authorize(args,false);
        var source=source(args);
        var policy=gate.currentForSubmission();
        return Map.of("case",source,"contentHash",hash(source),"policyHash",policy.contentHash(),
                "policy",parse(policy.rulesJson()),"comparison","EQUALS_EXACT_TEXT",
                "instructions","Submit every step by its 1-based position. Upload evidence for each required role. SUCCESS requires actual to equal the server-owned expected text; ERROR requires a differing actual. No task or lease required.");
    }

    public Map<String,Object> upload(Map<String,Object> args) {
        return operations.execute("functional.execution.evidence.upload",string(args,"requestId"),args,Map.class,
                () -> authorize(args,true), () -> {
                    var source=source(args); requireHash(source,args);
                    int step=number(args,"stepNum");
                    if(step<1 || step>source.getSteps().size()) fail("QUALITY_EVIDENCE_INVALID");
                    String purpose=string(args,"purpose");
                    if(!Set.of("BEFORE_STEP","AFTER_STEP").contains(purpose)) fail("QUALITY_EVIDENCE_INVALID");
                    String name=string(args,"fileName"), type=string(args,"contentType");
                    if(name.length()>200 || name.contains("/") || name.contains("\\") || name.chars().anyMatch(c->c<32 || c=='"')) fail("QUALITY_EVIDENCE_INVALID");
                    byte[] bytes=decodeImage(string(args,"base64"),type);
                    String suffix="image/png".equals(type)?".png":".jpg";
                    if(!(name.toLowerCase(Locale.ROOT).endsWith(suffix) || "image/jpeg".equals(type) && name.toLowerCase(Locale.ROOT).endsWith(".jpeg"))) fail("QUALITY_EVIDENCE_INVALID");
                    var token=AgentTokenContext.get();
                    // Serialize a token's pending upload quota, including concurrent requests.
                    jdbc.queryForList("SELECT id FROM agent_token WHERE id=? FOR UPDATE",token.getId());
                    Long pending=jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM agent_case_evidence WHERE token_id=? AND submission_id IS NULL",Long.class,token.getId());
                    if(pending!=null && pending+bytes.length>50L*1024*1024) fail("STEP_FACTS_LIMIT_EXCEEDED");
                    String id=UUID.randomUUID().toString(); long now=System.currentTimeMillis();
                    jdbc.update("INSERT INTO agent_case_evidence(id,token_id,user_id,project_id,case_id,content_hash,step_num,purpose,file_name,content_type,size_bytes,sha256,payload,create_time,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            id,token.getId(),token.getUserId(),args.get("projectId"),args.get("caseId"),args.get("contentHash"),step,purpose,name,type,bytes.length,DigestUtils.sha256Hex(bytes),bytes,now,now+86400000L);
                    return Map.of("evidenceId",id,"sha256",DigestUtils.sha256Hex(bytes),"sizeBytes",bytes.length,"expiresAt",now+86400000L);
                });
    }

    public Map<String,Object> report(Map<String,Object> args) {
        authorize(args,false);
        var rows=jdbc.queryForList("SELECT id,result,gate_status,review_code,reviewer_version,content_hash,policy_hash,facts,create_time FROM agent_case_submission WHERE id=? AND case_id=? AND project_id=?",
                string(args,"submissionId"),string(args,"caseId"),string(args,"projectId"));
        if(rows.size()!=1) throw AgentBusinessAccess.denied();
        var result=new LinkedHashMap<>(rows.getFirst());
        result.put("facts",parse(io.metersphere.agent.security.AgentSensitiveDataSanitizer.sanitize(cipher.reveal((String)result.get("facts")))));
        // Keep legacy fields readable while exposing the same status vocabulary as submit.
        String gateStatus = (String) result.get("gate_status");
        result.put("submissionId", result.get("id"));
        result.put("caseId", args.get("caseId"));
        result.put("gateStatus", gateStatus);
        result.put("publishStatus", "NEEDS_REVIEW".equals(gateStatus) ? "NOT_PUBLISHED" : "PUBLISHED");
        result.put("policyHash", result.get("policy_hash"));
        result.put("contentHash", result.get("content_hash"));
        result.put("reviewerVersion", result.get("reviewer_version"));
        result.put("issues", "NEEDS_REVIEW".equals(gateStatus)
                ? List.of(Map.of("ruleId", "Q-EVIDENCE-CONTENT", "code", Objects.toString(result.get("review_code"), "EVIDENCE_REVIEW_UNAVAILABLE"),
                    "message", "证据内容尚未通过独立核验；正式附件和执行结果未更新"))
                : List.of());
        return result;
    }

    public Map<String,Object> submit(Map<String,Object> args) {
        authorize(args,true);
        var replay=operations.completed("functional.execution.submit",string(args,"requestId"),args,Map.class,()->authorize(args,true));
        if(replay.isPresent()) return replay.get();
        // External content inspection happens before the short publishing transaction.
        // The transaction below rechecks source/policy and every evidence reference.
        var contentReview=reviewContent(args);
        return operations.execute("functional.execution.submit",string(args,"requestId"),args,Map.class,
                () -> authorize(args,true), () -> {
                    // Same case row as normal editing; no stale source may be published.
                    jdbc.queryForList("SELECT id FROM functional_case WHERE id=? FOR UPDATE",args.get("caseId"));
                    var source=source(args); requireHash(source,args);
                    var policy=gate.currentForSubmission();
                    if(!policy.contentHash().equals(string(args,"policyHash"))) fail("QUALITY_POLICY_VERSION_CONFLICT");
                    JsonNode steps=MAPPER.valueToTree(args.get("steps"));
                    if(steps==null || !steps.isArray() || steps.size()!=source.getSteps().size()) fail("QUALITY_FINAL_FACTS_INCOMPLETE");
                    var token=AgentTokenContext.get(); long now=System.currentTimeMillis();
                    Set<Integer> positions=new HashSet<>(); Set<String> evidenceIds=new LinkedHashSet<>();
                    List<Map<String,Object>> evidence=new ArrayList<>(); boolean failed=false;
                    for(JsonNode step:steps) {
                        if(!step.path("stepNum").isIntegralNumber() || !step.path("stepNum").canConvertToInt()) fail("QUALITY_FINAL_FACTS_INCOMPLETE");
                        int pos=step.path("stepNum").intValue();
                        if(pos<1 || pos>source.getSteps().size() || !positions.add(pos)) fail("QUALITY_FINAL_FACTS_INCOMPLETE");
                        String actual=step.path("actual").asText("");
                        if(!step.path("actual").isTextual() || actual.isBlank() || actual.length()>20000) fail("QUALITY_ASSERTION_INVALID");
                        if(!step.path("evidenceIds").isArray() || step.path("evidenceIds").isEmpty() || step.path("evidenceIds").size()>10) fail("QUALITY_EVIDENCE_REQUIRED");
                        List<AgentExecutionArtifactDTO> artifacts=new ArrayList<>();
                        for(JsonNode ref:step.path("evidenceIds")) {
                            if(!ref.isTextual() || !evidenceIds.add(ref.asText()) || evidenceIds.size()>100) fail("QUALITY_EVIDENCE_INVALID");
                            var rows=jdbc.queryForList("SELECT id,file_name,content_type,size_bytes,sha256,purpose FROM agent_case_evidence WHERE id=? AND token_id=? AND user_id=? AND project_id=? AND case_id=? AND content_hash=? AND step_num=? AND submission_id IS NULL AND expires_at>? FOR UPDATE",
                                    ref.asText(),token.getId(),token.getUserId(),args.get("projectId"),args.get("caseId"),args.get("contentHash"),pos,now);
                            if(rows.size()!=1) fail("QUALITY_EVIDENCE_INVALID");
                            var row=rows.getFirst(); evidence.add(row);
                            var artifact=new AgentExecutionArtifactDTO();
                            artifact.setPurpose((String)row.get("purpose")); artifact.setContentType((String)row.get("content_type"));
                            artifact.setSizeBytes(((Number)row.get("size_bytes")).longValue()); artifacts.add(artifact);
                        }
                        boolean success=gate.verifyDirectStep(policy,source.getSteps().get(pos-1).getExpected(),actual,artifacts);
                        if(!(success?"SUCCESS":"ERROR").equals(step.path("result").asText())) fail("QUALITY_FAILURE_FACTS_MISMATCH");
                        failed|=!success;
                    }
                    String id=UUID.randomUUID().toString(), result=failed?"ERROR":"SUCCESS";
                    var facts=Map.of("case",source,"steps",steps,"policy",parse(policy.rulesJson()),"evidence",evidence);
                    String gateStatus=contentReview.supported()?"ACCEPTED":"NEEDS_REVIEW";
                    jdbc.update("INSERT INTO agent_case_submission(id,token_id,user_id,project_id,case_id,content_hash,policy_hash,facts,result,create_time,gate_status,review_code,reviewer_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            id,token.getId(),token.getUserId(),args.get("projectId"),args.get("caseId"),args.get("contentHash"),policy.contentHash(),cipher.protect(JSON.toJSONString(facts)),contentReview.supported()?result:"UNKNOWN",now,gateStatus,contentReview.code(),contentReview.reviewerVersion());
                    for (String evidenceId:evidenceIds) {
                        jdbc.update("INSERT INTO agent_case_submission_evidence(submission_id,evidence_id,retain_until) VALUES (?,?,?)",
                                id,evidenceId,now+30L*86400000);
                    }
                    if (!contentReview.supported()) {
                        return Map.of("submissionId",id,"caseId",args.get("caseId"),"result","UNKNOWN",
                                "gateStatus",gateStatus,"publishStatus","NOT_PUBLISHED","attachmentIds",List.of(),
                                "issues",List.of(Map.of("ruleId","Q-EVIDENCE-CONTENT","code",contentReview.code(),
                                        "message","证据内容尚未通过独立核验；正式附件和执行结果未更新，请查看提交记录后补交或联系管理员配置核验服务")),
                                "policyHash",policy.contentHash());
                    }
                    for(var file:evidence) {
                        jdbc.update("UPDATE agent_case_evidence SET submission_id=? WHERE id=?",id,file.get("id"));
                        jdbc.update("INSERT INTO functional_case_attachment(id,case_id,file_id,file_name,size,local,create_user,create_time,file_source) VALUES (?,?,?,?,?,1,?,?, 'ATTACHMENT')",
                                UUID.randomUUID().toString(),args.get("caseId"),file.get("id"),file.get("file_name"),file.get("size_bytes"),token.getUserId(),now);
                    }
                    jdbc.update("UPDATE functional_case SET last_execute_result=?,last_execute_user=?,last_execute_time=? WHERE id=?",result,token.getUserId(),now,args.get("caseId"));
                    return Map.of("submissionId",id,"caseId",args.get("caseId"),"result",result,"gateStatus","ACCEPTED","publishStatus","PUBLISHED","attachmentIds",evidenceIds,"policyHash",policy.contentHash());
                });
    }

    private io.metersphere.agent.quality.EvidenceContentReviewer.Review reviewContent(Map<String,Object> args) {
        var source=source(args); requireHash(source,args);
        var submitted=MAPPER.valueToTree(args.get("steps"));
        if (!submitted.isArray() || submitted.size()!=source.getSteps().size()) fail("QUALITY_FINAL_FACTS_INCOMPLETE");
        List<Map<String,Object>> reviewSteps=new ArrayList<>();
        Set<Integer> positions=new HashSet<>();
        Set<String> evidenceIds=new HashSet<>();
        long totalBytes=0;
        var token=AgentTokenContext.get();
        for (JsonNode step:submitted) {
            int position=step.path("stepNum").asInt();
            if(position<1 || position>source.getSteps().size() || !positions.add(position)) fail("QUALITY_FINAL_FACTS_INCOMPLETE");
            if(!step.path("actual").isTextual() || step.path("actual").asText().isBlank() || step.path("actual").asText().length()>20000) fail("QUALITY_ASSERTION_INVALID");
            if(!step.path("evidenceIds").isArray() || step.path("evidenceIds").isEmpty() || step.path("evidenceIds").size()>10) fail("QUALITY_EVIDENCE_REQUIRED");
            List<Map<String,Object>> files=new ArrayList<>();
            List<String> hashes=new ArrayList<>();
            for (JsonNode ref:step.path("evidenceIds")) {
                if(!ref.isTextual() || !evidenceIds.add(ref.asText()) || evidenceIds.size()>100) fail("QUALITY_EVIDENCE_INVALID");
                var rows=jdbc.queryForList("SELECT sha256,content_type,purpose,payload FROM agent_case_evidence WHERE id=? AND token_id=? AND user_id=? AND project_id=? AND case_id=? AND content_hash=? AND step_num=? AND submission_id IS NULL AND expires_at>?",
                        ref.asText(),token.getId(),token.getUserId(),args.get("projectId"),args.get("caseId"),args.get("contentHash"),position,System.currentTimeMillis());
                if(rows.size()!=1) fail("QUALITY_EVIDENCE_INVALID");
                var row=rows.getFirst();
                totalBytes+=((byte[])row.get("payload")).length;
                if(totalBytes>50L*1024*1024) fail("STEP_FACTS_LIMIT_EXCEEDED");
                hashes.add((String)row.get("sha256"));
                files.add(Map.of("sha256",row.get("sha256"),"contentType",row.get("content_type"),"purpose",row.get("purpose"),
                        "base64",Base64.getEncoder().encodeToString((byte[])row.get("payload"))));
            }
            reviewSteps.add(Map.of("stepNum",position,"description",source.getSteps().get(position-1).getDesc(),
                    "expected",source.getSteps().get(position-1).getExpected(),"actual",step.path("actual").asText(),
                    "evidenceHashes",hashes,"evidence",files));
        }
        return evidenceReviewer.review(Map.of("protocolVersion","1","contentHash",args.get("contentHash"),"steps",reviewSteps));
    }

    private AgentCaseDTO source(Map<String,Object> args) {
        var source=cases.getById(string(args,"caseId"),true,null);
        if(source.getSteps()==null || source.getSteps().isEmpty() || source.getSteps().size()>100) fail("CASE_CONTENT_MISSING");
        for(var step:source.getSteps()) {
            if(step.getDesc()==null || step.getDesc().isBlank() || step.getExpected()==null || step.getExpected().isBlank()) fail("CASE_CONTENT_MISSING");
            step.setActualResult(null); step.setExecuteResult(null);
        }
        source.setLastExecuteResult(null); source.setLastExecuteUser(null); source.setLastExecuteUserName(null);
        return source;
    }
    private String hash(AgentCaseDTO source) { return operations.hash(MAPPER.convertValue(source,Map.class)); }
    private void requireHash(AgentCaseDTO source,Map<String,Object> args) {
        if(!hash(source).equals(string(args,"contentHash"))) fail("VERSION_CONFLICT");
    }
    static byte[] decodeImage(String base64,String type) {
        if(base64.length()>((MAX_FILE+2)/3)*4 || !Set.of("image/png","image/jpeg").contains(type)) throw new MSException("QUALITY_EVIDENCE_INVALID");
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
        } catch(Exception error) { throw new MSException("QUALITY_EVIDENCE_INVALID"); }
    }
    @Scheduled(fixedDelay=3600000)
    public void expirePendingEvidence() {
        long now=System.currentTimeMillis();
        jdbc.update("DELETE FROM agent_case_evidence WHERE submission_id IS NULL AND expires_at<? AND NOT EXISTS (SELECT 1 FROM agent_case_submission_evidence r WHERE r.evidence_id=agent_case_evidence.id AND r.retain_until>?) LIMIT 1000",now,now);
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
