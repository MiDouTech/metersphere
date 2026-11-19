package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionStepResultDTO;
import io.metersphere.agent.dto.AgentExecutionStepSubmitRequest;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.security.AgentSensitiveDataSanitizer;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.dao.DuplicateKeyException;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentExecutionStepResultService {
    @jakarta.annotation.Resource private io.metersphere.agent.quality.GlobalQualityGate qualityGate;
    @jakarta.annotation.Resource private AgentQualityAuditService qualityAudit;
    @jakarta.annotation.Resource private AgentExecutionFinalizationService finalization;
    @jakarta.annotation.Resource private io.metersphere.agent.security.AgentFactCipher factCipher;
    @jakarta.annotation.Resource private org.springframework.jdbc.core.JdbcTemplate jdbc;
    static final String RETRY_RECEIPT = "msp:write-retry-consumed";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentExecutionStepResultService.class);
    @jakarta.annotation.Resource private AgentTestPlanQueryService testPlanQueryService;
    private static final String WAITING_WRITE = "WAITING_WRITE_RECONCILIATION";
    @org.springframework.beans.factory.annotation.Value("${agent.execution.write-recovery-public-key:}")
    private String writeRecoveryPublicKey = "";

    private static final Set<String> STATUSES = Set.of("SUCCESS", "FAILED", "BLOCKED", "SKIPPED");

    private final AgentExecutionMapper mapper;
    private final AgentRunnerService runnerService;

    public AgentExecutionStepResultService(AgentExecutionMapper mapper, AgentRunnerService runnerService) {
        this.mapper = mapper;
        this.runnerService = runnerService;
    }

    public AgentExecutionStepResultDTO submit(AgentExecutionStepSubmitRequest request) {
        if(request!=null){
            String trace=request.getTraceId();
            if(trace==null || !trace.matches("[A-Za-z0-9_-]{1,64}"))trace=java.util.UUID.randomUUID().toString();
            request.setTraceId(trace);
            var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if(attributes!=null)attributes.setAttribute("agentTraceId",trace,org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        }
        Map<String,String> auditHashes=new java.util.HashMap<>();
        try {
            return response(accept(request,auditHashes,false));
        }
        catch(MSException failure){
            String code=failure.getMessage();
            if(code!=null && (code.startsWith("QUALITY_") || code.startsWith("STEP_FACTS_") || code.startsWith("STEP_RESULT_REQUEST_CONFLICT")))
                qualityAudit.rejected(request,code,request!=null && StringUtils.isNotBlank(request.getTraceId()) ? StringUtils.left(request.getTraceId(),64) : java.util.UUID.randomUUID().toString(),auditHashes);
            throw failure;
        }
    }

    public boolean claimWriteRetry(AgentExecutionStepSubmitRequest request, String waitingExecutionId,
                                   String waitingRequestId, String businessKeyHash, String proof) {
        AgentRunnerLeaseDTO lease = runnerService.requireActiveLease("Bearer " + request.getLeaseToken(), request.getLeaseId());
        if (!request.getTaskId().equals(lease.getTaskId()) || !request.getExecutionId().equals(lease.getExecutionId()))
            throw new MSException("TASK_NOT_FOUND_OR_NOT_ACCESSIBLE");
        finalization.requireMutable(request.getTaskId(), request.getExecutionId());
        if (!businessKeyHash.matches("[a-f0-9]{64}") || !validRecoveryProof(request, waitingExecutionId,
                waitingRequestId, businessKeyHash, proof)) {
            throw recoveryRejected("WRITE_RECOVERY_PROOF_INVALID");
        }
        var rows = jdbc.queryForList("""
                SELECT request_id FROM ai_execution_step_result
                WHERE task_id=? AND step_id=? AND request_id=? FOR UPDATE
                """, String.class, request.getTaskId(), request.getStepId(), RETRY_RECEIPT);
        if (!rows.isEmpty()) {
            LOG.info("event=WRITE_RECOVERY stage=consume result=ALREADY_CONSUMED");
            return false;
        }
        var facts = jdbc.query("""
                SELECT execution_id, request_id, status, error_code, input_snapshot
                FROM ai_execution_step_result
                WHERE task_id=? AND step_id=?
                ORDER BY create_time DESC, id DESC LIMIT 1 FOR UPDATE
                """, org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(AgentExecutionStepResultDTO.class),
                request.getTaskId(), request.getStepId());
        if (facts.isEmpty()) throw recoveryRejected("WRITE_RECOVERY_WAITING_RECEIPT_INVALID");
        var waiting = facts.getFirst();
        if (!waitingExecutionId.equals(waiting.getExecutionId()) || !waitingRequestId.equals(waiting.getRequestId())
                || !"BLOCKED".equals(waiting.getStatus()) || !WAITING_WRITE.equals(waiting.getErrorCode())
                || !businessKeyHash.equals(waiting.getInputSnapshot())) {
            throw recoveryRejected("WRITE_RECOVERY_WAITING_RECEIPT_INVALID");
        }
        request.setTraceId(java.util.UUID.randomUUID().toString());
        request.setRequestId(RETRY_RECEIPT);
        request.setStatus("BLOCKED");
        request.setErrorCode(WAITING_WRITE);
        request.setArtifactIds(List.of());
        accept(request, new java.util.HashMap<>(), true);
        LOG.info("event=WRITE_RECOVERY stage=consume result=GRANTED traceId={}", request.getTraceId());
        return true;
    }

    private MSException recoveryRejected(String code) {
        LOG.info("event=WRITE_RECOVERY stage=verify result={}", code);
        return new MSException(code);
    }

    private boolean validRecoveryProof(AgentExecutionStepSubmitRequest request, String waitingExecutionId,
                                       String waitingRequestId, String businessKeyHash, String proof) {
        if (writeRecoveryPublicKey.isBlank() || proof.length() > 256) return false;
        try {
            String[] parts = proof.split("[.]", -1);
            if (parts.length != 2) return false;
            long expires = Long.parseLong(parts[0]);
            long now = System.currentTimeMillis();
            if (expires <= now || expires - now > 120_000L) return false;
            var key = java.security.KeyFactory.getInstance("Ed25519").generatePublic(
                    new java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(writeRecoveryPublicKey)));
            var verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            String payload = JSON.toJSONString(List.of("MSP_TERMINAL_NO_EFFECT_AND_CONDITION_CHANGED_V1",
                    request.getTaskId(), request.getExecutionId(), request.getLeaseId(), request.getStepId(),
                    waitingExecutionId, waitingRequestId, businessKeyHash, parts[0]));
            verifier.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return verifier.verify(java.util.Base64.getUrlDecoder().decode(parts[1]));
        } catch (java.security.GeneralSecurityException | IllegalArgumentException invalid) {
            return false;
        }
    }

    @Transactional(readOnly=true)
    public AgentExecutionStepResultDTO read(String taskId,String executionId,String stepId,String requestId){
        var rows=jdbc.query("SELECT * FROM ai_execution_step_result WHERE task_id=? AND execution_id=? AND step_id=? AND request_id=?",
                org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(AgentExecutionStepResultDTO.class),taskId,executionId,stepId,requestId);
        if(rows.size()!=1)throw new MSException("STEP_RESULT_NOT_FOUND");return response(rows.getFirst());
    }
    private AgentExecutionStepResultDTO response(AgentExecutionStepResultDTO stored){
        var response=new AgentExecutionStepResultDTO();org.springframework.beans.BeanUtils.copyProperties(stored,response);
        if(io.metersphere.agent.security.AgentFactCipher.protectedValue(response.getAssertionResult()))response.setAssertionResult(null);
        if(io.metersphere.agent.security.AgentFactCipher.protectedValue(response.getInputSnapshot()))response.setInputSnapshot(null);
        if(io.metersphere.agent.security.AgentFactCipher.protectedValue(response.getOutputSummary()))response.setOutputSummary(null);
        return response;
    }
    private AgentExecutionStepResultDTO accept(AgentExecutionStepSubmitRequest request,Map<String,String> auditHashes,boolean recoveryClaim) {
        if (request == null
                || StringUtils.isAnyBlank(request.getTaskId(), request.getExecutionId(), request.getLeaseId(),
                request.getLeaseToken(), request.getStepId(), request.getStatus(), request.getRequestId())) {
            throw new MSException("STEP_RESULT_REQUEST_INVALID");
        }
        AgentRunnerLeaseDTO lease = runnerService.requireActiveLease("Bearer " + request.getLeaseToken(), request.getLeaseId());
        AgentExecutionTaskDTO task = mapper.selectTaskById(request.getTaskId());
        if (task == null || !StringUtils.equals(lease.getTaskId(), task.getId())
                || !StringUtils.equals(lease.getExecutionId(), request.getExecutionId())
                || !StringUtils.equals(task.getRunnerLeaseId(), lease.getId())) {
            throw new MSException("TASK_NOT_FOUND_OR_NOT_ACCESSIBLE");
        }
        if (!recoveryClaim && RETRY_RECEIPT.equalsIgnoreCase(request.getRequestId()))
            throw new MSException("STEP_RESULT_REQUEST_INVALID");
        AgentStepFacts.bounded(request.getRequestId(),64);
        finalization.requireMutable(request.getTaskId(),request.getExecutionId());
        String status = StringUtils.upperCase(StringUtils.trim(request.getStatus()));
        if (!STATUSES.contains(status)) {
            throw new MSException("STEP_RESULT_STATUS_INVALID");
        }
        AgentExecutionStepDTO step = mapper.selectStepsByTaskId(task.getId()).stream()
                .filter(item -> StringUtils.equals(item.getId(), request.getStepId()))
                .findFirst().orElseThrow(() -> new MSException("STEP_NOT_FOUND_OR_NOT_ACCESSIBLE"));
        if (request.getAttempt() != null && !request.getAttempt().equals(lease.getAttempt())) throw new MSException("QUALITY_ATTEMPT_INVALID");
        List<String> artifactIds = request.getArtifactIds() == null ? List.of() : request.getArtifactIds().stream().distinct().sorted().toList();
        if(artifactIds.size()>256)throw new MSException("STEP_FACTS_LIMIT_EXCEEDED");
        boolean v2=task.getExecutionContract()!=null && "v2".equals(JSON.parseObject(task.getExecutionContract(),Map.class).get("contractVersion"));
        String assertions=AgentStepFacts.assertions(request.getAssertionResult(),v2);
        var policy=qualityGate.binding(request.getExecutionId(),task.getId());
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("version",AgentStepFacts.VERSION);payload.put("contractVersion",v2?"v2":"v1");payload.put("taskId",task.getId());payload.put("executionId",request.getExecutionId());
        payload.put("stepId",step.getId());payload.put("attempt",lease.getAttempt());payload.put("status",status);
        payload.put("contractHash",task.getExecutionContractHash());payload.put("policyHash",policy.contentHash());
        payload.put("assertions",assertions);payload.put("artifacts",artifactIds);
        payload.put("input",AgentStepFacts.bounded(request.getInputSnapshot(),65535));payload.put("output",AgentStepFacts.bounded(request.getOutputSummary(),65535));
        payload.put("errorCode",AgentStepFacts.bounded(request.getErrorCode(),64));payload.put("errorMessage",AgentStepFacts.bounded(request.getErrorMessage(),1000));
        payload.put("startedAt",request.getStartedAt());payload.put("finishedAt",request.getFinishedAt());
        var requestedKnowledge = request.getUsedKnowledgeRefs();
        List<String> knowledge = List.of();
        if (requestedKnowledge != null && !requestedKnowledge.isEmpty()) {
            if (requestedKnowledge.size() > 32 || requestedKnowledge.stream().anyMatch(ref -> ref == null
                    || !ref.matches("[A-Za-z0-9_.:-]{1,128}:v[1-9][0-9]{0,17}"))
                    || StringUtils.isBlank(request.getTestPlanCaseId()) || !"SUCCESS".equals(status)) {
                throw new MSException("STEP_KNOWLEDGE_REFERENCE_INVALID");
            }
            knowledge = requestedKnowledge.stream().distinct().sorted().toList();
            payload.put("testPlanCaseId", request.getTestPlanCaseId());
            payload.put("usedKnowledgeRefs", knowledge);
        }
        String payloadHash=AgentStepFacts.hash(payload);
        auditHashes.put("payload",payloadHash);auditHashes.put("contract",task.getExecutionContractHash());auditHashes.put("policy",policy.contentHash());
        AgentExecutionStepResultDTO existing = mapper.selectStepResultByRequest(request.getExecutionId(),request.getStepId(),request.getRequestId());
        if(existing!=null)return replay(existing,payloadHash);
        var used = knowledge.isEmpty() ? List.<Map.Entry<String, Long>>of()
                : testPlanQueryService.validateExecutionKnowledge(task, request.getTestPlanCaseId(), step.getId(), knowledge);
        for (String artifactId : artifactIds) {
            AgentExecutionArtifactDTO artifact = mapper.selectArtifactById(artifactId);
            if (artifact == null || !StringUtils.equals(task.getId(), artifact.getTaskId())
                    || !StringUtils.equals(request.getExecutionId(), artifact.getExecutionId())
                    || !"AVAILABLE".equals(artifact.getStatus())) {
                throw new MSException("ARTIFACT_NOT_FOUND_OR_NOT_ACCESSIBLE");
            }
        }
        if (request.getAttempt() != null && !request.getAttempt().equals(lease.getAttempt())) {
            throw new MSException("QUALITY_ATTEMPT_INVALID");
        }
        if ("SUCCESS".equals(status)) qualityGate.verifySuccess(task, request.getExecutionId(), step,
                assertions, artifactIds);
        if ("FAILED".equals(status)) qualityGate.verifyFailure(task,request.getExecutionId(),step,assertions,artifactIds);
        long now = System.currentTimeMillis();
        AgentExecutionStepResultDTO result = new AgentExecutionStepResultDTO();
        result.setId(IDGenerator.nextStr());
        result.setTaskId(task.getId());
        result.setExecutionId(request.getExecutionId());
        result.setLeaseId(lease.getId());
        result.setStepId(step.getId());
        result.setAttempt(request.getAttempt() == null ? lease.getAttempt() : request.getAttempt());
        result.setStatus(status);
        result.setInputSnapshot(factCipher.protect(request.getInputSnapshot()));
        result.setOutputSummary(factCipher.protect(request.getOutputSummary()));
        result.setAssertionResult(factCipher.protect(assertions));
        result.setPayloadHash(payloadHash);
        result.setFactsVersion(AgentStepFacts.VERSION);
        result.setErrorCode(StringUtils.abbreviate(request.getErrorCode(), 64));
        result.setErrorMessage(StringUtils.abbreviate(
                AgentSensitiveDataSanitizer.sanitize(request.getErrorMessage()), 1000));
        result.setArtifactIds(artifactIds.isEmpty() ? null : JSON.toJSONString(artifactIds));
        result.setRequestId(request.getRequestId());
        result.setTraceId(StringUtils.defaultIfBlank(request.getTraceId(), task.getTraceId()));
        result.setStartedAt(request.getStartedAt());
        result.setFinishedAt(request.getFinishedAt() == null ? now : request.getFinishedAt());
        result.setCreateTime(now);
        result.setCreateUser("executor:" + lease.getLeaseOwnerId());
        try {mapper.insertStepResult(result);}
        catch(DuplicateKeyException collision){
            AgentExecutionStepResultDTO concurrent=mapper.selectStepResultByRequest(request.getExecutionId(),request.getStepId(),request.getRequestId());
            if(concurrent==null)throw new MSException("STEP_RESULT_REQUEST_CONFLICT");
            return replay(concurrent,payloadHash);
        }
        for (var reference : used) {
            jdbc.update("""
                    INSERT INTO ai_execution_knowledge_usage (step_result_id, knowledge_id, knowledge_version)
                    VALUES (?, ?, ?)
                    """, result.getId(), reference.getKey(), reference.getValue());
        }
        mapper.markStepCompleted(task.getId(), step.getId(), status, AgentSensitiveDataSanitizer.sanitize(request.getOutputSummary()),
                result.getErrorMessage(), result.getErrorCode(), false, now);
        return result;
    }
    private AgentExecutionStepResultDTO replay(AgentExecutionStepResultDTO existing,String hash){
        if(existing.getPayloadHash()==null)throw new MSException("STEP_RESULT_LEGACY_REQUEST_CONFLICT");
        if(!hash.equals(existing.getPayloadHash()))throw new MSException("STEP_RESULT_REQUEST_CONFLICT");
        return existing;
    }
}
