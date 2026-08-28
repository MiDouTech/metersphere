package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.dto.AgentExecutionArtifactUploadResponse;
import io.metersphere.agent.dto.AgentArtifactPrepareRequest;
import io.metersphere.agent.dto.AgentArtifactPrepareResponse;
import io.metersphere.agent.dto.AgentArtifactCommitRequest;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.FileCenter;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.service.CommonFileService;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentExecutionArtifactService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> PURPOSES = Set.of(
            "BEFORE_STEP", "AFTER_STEP", "FAILURE", "HEALING_BEFORE", "HEALING_AFTER");

    @Resource
    private AgentExecutionMapper executionMapper;
    @Resource
    private AgentRunnerService runnerService;
    @Resource
    private AgentExecutionService executionService;
    @Resource
    private CommonFileService commonFileService;
    @Resource
    private TestAssetVersionService testAssetVersionService;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private AgentEvidenceRedactionService redactionService;
    @Value("${agent.execution.artifact-max-bytes:5242880}")
    private long artifactMaxBytes = 5L * 1024L * 1024L;
    @Value("${agent.execution.artifact-retention-ms:2592000000}")
    private long artifactRetentionMs = 30L * 24L * 60L * 60L * 1000L;
    @Value("${agent.execution.artifact-prepare-ttl-ms:900000}")
    private long artifactPrepareTtlMs = 15L * 60L * 1000L;

    public AgentArtifactPrepareResponse prepare(AgentArtifactPrepareRequest request) {
        requirePrepareRequest(request);
        AgentRunnerLeaseDTO lease = runnerService.requireActiveLease(bearer(request.getLeaseToken()), request.getLeaseId());
        AgentExecutionTaskDTO task = requireLeaseTask(request.getTaskId(), request.getExecutionId(), lease);
        String purpose = StringUtils.upperCase(StringUtils.trimToEmpty(request.getPurpose()));
        if (!PURPOSES.contains(purpose)) {
            throw new MSException("UNSUPPORTED_CONTRACT_VALUE: artifact.purpose");
        }
        if (!Boolean.TRUE.equals(request.getRedacted())) {
            throw new MSException("ARTIFACT_REDACTION_REQUIRED");
        }
        if (request.getSizeBytes() == null || request.getSizeBytes() <= 0
                || request.getSizeBytes() > artifactMaxBytes) {
            throw new MSException("ARTIFACT_SIZE_INVALID");
        }
        String expectedSha256 = StringUtils.lowerCase(StringUtils.trim(request.getSha256()));
        if (!expectedSha256.matches("[0-9a-f]{64}")) {
            throw new MSException("ARTIFACT_HASH_INVALID");
        }
        AgentExecutionArtifactDTO existing = executionMapper.selectArtifactByPrepareKey(task.getId(), request.getRequestId());
        if (existing != null) {
            return preparedResponse(existing, null);
        }
        long now = System.currentTimeMillis();
        String uploadToken = "msau_" + randomToken();
        AgentExecutionArtifactDTO artifact = new AgentExecutionArtifactDTO();
        artifact.setId(IDGenerator.nextStr());
        artifact.setTaskId(task.getId());
        artifact.setExecutionId(lease.getExecutionId());
        artifact.setLeaseId(lease.getId());
        artifact.setExecutionCaseId(validateScope(task.getId(), request.getCaseId(), request.getStepId()));
        artifact.setCaseId(StringUtils.trimToNull(request.getCaseId()));
        artifact.setStepId(StringUtils.trimToNull(request.getStepId()));
        artifact.setPurpose(purpose);
        artifact.setFileName(StringUtils.abbreviate(Path.of(request.getFileName()).getFileName().toString(), 255));
        artifact.setRedacted(true);
        artifact.setStatus("PREPARED");
        artifact.setUploadStatus("PREPARED");
        artifact.setExpectedSize(request.getSizeBytes());
        artifact.setExpectedSha256(expectedSha256);
        artifact.setExpectedContentType(StringUtils.abbreviate(request.getContentType(), 128));
        artifact.setUploadTokenHash(sha256(uploadToken.getBytes(StandardCharsets.UTF_8)));
        artifact.setIdempotencyKey(request.getRequestId());
        artifact.setPreparedAt(now);
        artifact.setRetentionUntil(now + artifactPrepareTtlMs);
        artifact.setTraceId(StringUtils.defaultIfBlank(request.getTraceId(), task.getTraceId()));
        artifact.setCreateTime(now);
        artifact.setCreateUser("executor:" + lease.getLeaseOwnerId());
        executionMapper.insertArtifact(artifact);
        return preparedResponse(artifact, uploadToken);
    }

    public AgentExecutionArtifactUploadResponse uploadPrepared(String authorization, String leaseId,
                                                               String artifactId, String uploadToken,
                                                               MultipartFile file) {
        AgentRunnerLeaseDTO lease = runnerService.requireActiveLease(authorization, leaseId);
        AgentExecutionArtifactDTO prepared = executionMapper.selectArtifactById(artifactId);
        requirePrepared(prepared, lease, uploadToken, "PREPARED");
        if (file == null || file.isEmpty() || file.getSize() != prepared.getExpectedSize()
                || file.getSize() > artifactMaxBytes) {
            throw new MSException("ARTIFACT_SIZE_INVALID");
        }
        byte[] bytes = readBytes(file);
        ArtifactType artifactType = detectArtifact(bytes, file);
        redactionService.scanBeforePersist(bytes,artifactType.contentType(),Boolean.TRUE.equals(prepared.getRedacted()));
        String actualSha256 = sha256(bytes);
        if (!MessageDigest.isEqual(actualSha256.getBytes(StandardCharsets.UTF_8),
                prepared.getExpectedSha256().getBytes(StandardCharsets.UTF_8))) {
            throw new MSException("ARTIFACT_HASH_MISMATCH");
        }
        if (!StringUtils.equalsIgnoreCase(artifactType.contentType(), prepared.getExpectedContentType())) {
            throw new MSException("ARTIFACT_CONTENT_TYPE_MISMATCH");
        }
        String safeName = "evidence-" + artifactId + artifactType.extension();
        MultipartFile namedFile = new NamedMultipartFile(file, safeName, artifactType.contentType(), bytes);
        String fileId = commonFileService.uploadTempImgFile(namedFile);
        String folder = "ai/execution/artifacts/" + lease.getTaskId() + "/" + lease.getExecutionId();
        commonFileService.saveFileFromTempFile(folder, Map.of(fileId, safeName));
        if (executionMapper.storePreparedArtifact(artifactId, leaseId, fileId, safeName, folder,
                artifactType.contentType(), bytes.length, actualSha256) != 1) {
            throw new MSException("ARTIFACT_PREPARE_CONFLICT");
        }
        return response(executionMapper.selectArtifactById(artifactId));
    }

    public AgentExecutionArtifactDTO commit(AgentArtifactCommitRequest request) {
        requireCommitRequest(request);
        AgentRunnerLeaseDTO lease = runnerService.requireActiveLease(bearer(request.getLeaseToken()), request.getLeaseId());
        AgentExecutionTaskDTO task = requireLeaseTask(request.getTaskId(), request.getExecutionId(), lease);
        AgentExecutionArtifactDTO artifact = executionMapper.selectArtifactById(request.getArtifactId());
        requirePrepared(artifact, lease, request.getUploadToken(), "UPLOADED");
        long now = System.currentTimeMillis();
        if (executionMapper.commitPreparedArtifact(artifact.getId(), lease.getId(), now,
                now + artifactRetentionMs,
                StringUtils.defaultIfBlank(request.getTraceId(), task.getTraceId())) != 1) {
            throw new MSException("ARTIFACT_COMMIT_CONFLICT");
        }
        artifact = executionMapper.selectArtifactById(artifact.getId());
        publishAssetRelations(task, artifact);
        return artifact;
    }

    private void requirePrepareRequest(AgentArtifactPrepareRequest request) {
        if (request == null
                || StringUtils.isAnyBlank(request.getTaskId(), request.getExecutionId(), request.getLeaseId(),
                request.getLeaseToken(), request.getPurpose(), request.getFileName(), request.getContentType(),
                request.getSha256(), request.getRequestId())) {
            throw new MSException("ARTIFACT_PREPARE_REQUEST_INVALID");
        }
    }

    private void requireCommitRequest(AgentArtifactCommitRequest request) {
        if (request == null
                || StringUtils.isAnyBlank(request.getTaskId(), request.getExecutionId(), request.getLeaseId(),
                request.getLeaseToken(), request.getArtifactId(), request.getUploadToken(), request.getRequestId())) {
            throw new MSException("ARTIFACT_COMMIT_REQUEST_INVALID");
        }
    }

    public AgentExecutionArtifactUploadResponse upload(String authorization, String leaseId, MultipartFile file,
                                                       String caseId, String stepId, String purpose,
                                                       String expectedSha256, Boolean redacted) {
        AgentRunnerLeaseDTO lease = runnerService.requireActiveLease(authorization, leaseId);
        AgentExecutionTaskDTO task = executionMapper.selectTaskById(lease.getTaskId());
        if (task == null || !leaseId.equals(task.getRunnerLeaseId())) {
            throw new MSException("RUNNER_LEASE_TASK_MISMATCH");
        }
        String normalizedPurpose = StringUtils.upperCase(StringUtils.trimToEmpty(purpose));
        if (!PURPOSES.contains(normalizedPurpose)) {
            throw new MSException("UNSUPPORTED_CONTRACT_VALUE: artifact.purpose");
        }
        if (!Boolean.TRUE.equals(redacted)) {
            throw new MSException("ARTIFACT_REDACTION_REQUIRED");
        }
        if (file == null || file.isEmpty() || file.getSize() > artifactMaxBytes) {
            throw new MSException("ARTIFACT_SIZE_INVALID");
        }
        byte[] bytes = readBytes(file);
        ArtifactType artifactType = detectArtifact(bytes, file);
        redactionService.scanBeforePersist(bytes,artifactType.contentType(),Boolean.TRUE.equals(redacted));
        String actualSha256 = sha256(bytes);
        if (StringUtils.isNotBlank(expectedSha256)
                && !MessageDigest.isEqual(actualSha256.getBytes(), StringUtils.lowerCase(expectedSha256).getBytes())) {
            throw new MSException("ARTIFACT_HASH_MISMATCH");
        }
        String executionCaseId = validateScope(task.getId(), caseId, stepId);
        AgentExecutionArtifactDTO existing = executionMapper.selectArtifactByIdentity(
                task.getId(), actualSha256, normalizedPurpose, StringUtils.trimToNull(stepId));
        if (existing != null) {
            publishAssetRelations(task, existing);
            return response(existing);
        }

        String artifactId = IDGenerator.nextStr();
        String fileName = "evidence-" + artifactId + artifactType.extension();
        MultipartFile namedFile = new NamedMultipartFile(file, fileName, artifactType.contentType(), bytes);
        String fileId = commonFileService.uploadTempImgFile(namedFile);
        String folder = "ai/execution/artifacts/" + task.getProjectId() + "/" + task.getId();
        commonFileService.saveFileFromTempFile(folder, Map.of(fileId, fileName));

        long now = System.currentTimeMillis();
        AgentExecutionArtifactDTO artifact = new AgentExecutionArtifactDTO();
        artifact.setId(artifactId);
        artifact.setTaskId(task.getId());
        artifact.setExecutionId(lease.getExecutionId());
        artifact.setLeaseId(lease.getId());
        artifact.setExecutionCaseId(executionCaseId);
        artifact.setCaseId(StringUtils.trimToNull(caseId));
        artifact.setStepId(StringUtils.trimToNull(stepId));
        artifact.setPurpose(normalizedPurpose);
        artifact.setFileId(fileId);
        artifact.setFileName(fileName);
        artifact.setStorageFolder(folder);
        artifact.setContentType(artifactType.contentType());
        artifact.setSizeBytes((long) bytes.length);
        artifact.setSha256(actualSha256);
        artifact.setRedacted(true);
        artifact.setStatus("AVAILABLE");
        artifact.setUploadStatus("AVAILABLE");
        artifact.setCommittedAt(now);
        artifact.setTraceId(task.getTraceId());
        artifact.setRetentionUntil(now + artifactRetentionMs);
        artifact.setCreateTime(now);
        artifact.setCreateUser("runner:" + lease.getRunnerId());
        executionMapper.insertArtifact(artifact);
        publishAssetRelations(task, artifact);
        return response(artifact);
    }

    void publishAssetRelations(AgentExecutionTaskDTO task, AgentExecutionArtifactDTO artifact) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assetType", "EVIDENCE");
        snapshot.put("assetId", artifact.getId());
        snapshot.put("taskId", artifact.getTaskId());
        snapshot.put("executionCaseId", artifact.getExecutionCaseId());
        snapshot.put("caseId", artifact.getCaseId());
        snapshot.put("stepId", artifact.getStepId());
        snapshot.put("purpose", artifact.getPurpose());
        snapshot.put("fileName", artifact.getFileName());
        snapshot.put("contentType", artifact.getContentType());
        snapshot.put("sizeBytes", artifact.getSizeBytes());
        snapshot.put("sha256", artifact.getSha256());
        snapshot.put("redacted", artifact.getRedacted());
        snapshot.put("retentionUntil", artifact.getRetentionUntil());
        var version = testAssetVersionService.publish(task.getProjectId(), "EVIDENCE", artifact.getId(),
                artifact.getSha256(), JSON.toJSONString(snapshot), artifact.getCreateUser());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("executionCaseId", artifact.getExecutionCaseId());
        metadata.put("stepId", artifact.getStepId());
        metadata.put("purpose", artifact.getPurpose());
        testAssetVersionService.relate(task.getProjectId(), "PRODUCES", "TASK", task.getId(), null,
                "EVIDENCE", artifact.getId(), version.getId(), JSON.toJSONString(metadata), artifact.getCreateUser());

        if (StringUtils.isNotBlank(artifact.getStepId())) {
            AgentExecutionStepDTO step = executionMapper.selectStepsByTaskId(task.getId()).stream()
                    .filter(item -> artifact.getStepId().equals(item.getId()))
                    .findFirst().orElse(null);
            if (step != null) {
                Map<String, Object> stepSnapshot = new LinkedHashMap<>();
                stepSnapshot.put("assetType", "STEP");
                stepSnapshot.put("assetId", step.getId());
                stepSnapshot.put("name", "Step " + step.getPos() + ": "
                        + StringUtils.abbreviate(StringUtils.defaultString(step.getInstruction()), 120));
                stepSnapshot.put("taskId", step.getTaskId());
                stepSnapshot.put("executionCaseId", step.getExecutionCaseId());
                stepSnapshot.put("caseId", step.getCaseId());
                stepSnapshot.put("instruction", step.getInstruction());
                stepSnapshot.put("expected", step.getExpected());
                stepSnapshot.put("status", step.getStatus());
                stepSnapshot.put("actualResult", step.getActualResult());
                stepSnapshot.put("failureCategory", step.getFailureCategory());
                String sourceVersion = step.getUpdateTime() != null ? String.valueOf(step.getUpdateTime())
                        : String.valueOf(step.getVersion());
                var stepVersion = testAssetVersionService.publish(task.getProjectId(), "STEP", step.getId(),
                        sourceVersion, JSON.toJSONString(stepSnapshot), artifact.getCreateUser());
                testAssetVersionService.relate(task.getProjectId(), "PRODUCES", "STEP", step.getId(),
                        stepVersion.getId(), "EVIDENCE", artifact.getId(), version.getId(),
                        JSON.toJSONString(metadata), artifact.getCreateUser());
            }
        }

        if (StringUtils.isNotBlank(artifact.getExecutionCaseId())) {
            AgentExecutionCaseDTO executionCase = executionMapper.selectCasesByTaskId(task.getId()).stream()
                    .filter(item -> artifact.getExecutionCaseId().equals(item.getId()))
                    .findFirst().orElse(null);
            if (executionCase != null) {
                FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(executionCase.getCaseId());
                if (functionalCase != null && task.getProjectId().equals(functionalCase.getProjectId())) {
                    String stableCaseId = StringUtils.defaultIfBlank(functionalCase.getRefId(), functionalCase.getId());
                    testAssetVersionService.relate(task.getProjectId(), "PRODUCES", "CASE", stableCaseId,
                            executionCase.getAssetVersionId(), "EVIDENCE", artifact.getId(), version.getId(),
                            JSON.toJSONString(metadata), artifact.getCreateUser());
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AgentExecutionArtifactDTO> list(String taskId) {
        executionService.get(taskId);
        List<AgentExecutionArtifactDTO> artifacts = executionMapper.selectArtifactsByTaskId(taskId);
        artifacts.forEach(artifact -> artifact.setDownloadPath(
                "/ai/execution/task/" + taskId + "/artifact/" + artifact.getId()));
        return artifacts;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> download(String taskId, String artifactId) {
        executionService.get(taskId);
        AgentExecutionArtifactDTO artifact = executionMapper.selectArtifactById(artifactId);
        if (artifact == null || !taskId.equals(artifact.getTaskId()) || !"AVAILABLE".equals(artifact.getStatus())
                || !Boolean.TRUE.equals(artifact.getRedacted())) {
            throw new MSException("ARTIFACT_NOT_FOUND");
        }
        try {
            FileRequest request = new FileRequest();
            request.setFolder(artifact.getStorageFolder() + "/" + artifact.getFileId());
            request.setFileName(artifact.getFileName());
            byte[] bytes = FileCenter.getDefaultRepository().getFile(request);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + artifact.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(artifact.getContentType()))
                    .body(bytes);
        } catch (Exception ex) {
            throw new MSException("ARTIFACT_DOWNLOAD_FAILED", ex);
        }
    }

    @Scheduled(initialDelay = 60_000L, fixedDelay = 3_600_000L)
    public void cleanupExpiredArtifacts() {
        for (AgentExecutionArtifactDTO artifact : executionMapper.selectExpiredArtifacts(System.currentTimeMillis(), 200)) {
            try {
                FileRequest request = new FileRequest();
                request.setFolder(artifact.getStorageFolder() + "/" + artifact.getFileId());
                request.setFileName(artifact.getFileName());
                FileCenter.getDefaultRepository().delete(request);
                executionMapper.markArtifactDeleted(artifact.getId(), "DELETED");
            } catch (Exception ex) {
                LogUtils.error("AI execution artifact cleanup failed, artifactId={}", artifact.getId(), ex);
            }
        }
    }

    private String validateScope(String taskId, String caseId, String stepId) {
        if (StringUtils.isBlank(caseId) && StringUtils.isBlank(stepId)) {
            return null;
        }
        List<AgentExecutionCaseDTO> cases = executionMapper.selectCasesByTaskId(taskId);
        AgentExecutionCaseDTO executionCase = cases.stream()
                .filter(item -> StringUtils.equals(caseId, item.getCaseId()) || StringUtils.equals(caseId, item.getId()))
                .findFirst().orElseThrow(() -> new MSException("ARTIFACT_CASE_MISMATCH"));
        if (StringUtils.isNotBlank(stepId)) {
            AgentExecutionStepDTO step = executionMapper.selectStepsByTaskId(taskId).stream()
                    .filter(item -> stepId.equals(item.getId()))
                    .findFirst().orElseThrow(() -> new MSException("ARTIFACT_STEP_MISMATCH"));
            if (!executionCase.getId().equals(step.getExecutionCaseId())) {
                throw new MSException("ARTIFACT_STEP_MISMATCH");
            }
        }
        return executionCase.getId();
    }

    private AgentExecutionTaskDTO requireLeaseTask(String taskId, String executionId, AgentRunnerLeaseDTO lease) {
        AgentExecutionTaskDTO task = executionMapper.selectTaskById(lease.getTaskId());
        if (task == null || !StringUtils.equals(taskId, task.getId())
                || !StringUtils.equals(executionId, lease.getExecutionId())
                || !StringUtils.equals(lease.getId(), task.getRunnerLeaseId())) {
            throw new MSException("TASK_NOT_FOUND_OR_NOT_ACCESSIBLE");
        }
        return task;
    }

    private void requirePrepared(AgentExecutionArtifactDTO artifact, AgentRunnerLeaseDTO lease,
                                 String uploadToken, String uploadStatus) {
        String tokenHash = sha256(StringUtils.defaultString(uploadToken).getBytes(StandardCharsets.UTF_8));
        if (artifact == null || !StringUtils.equals(lease.getTaskId(), artifact.getTaskId())
                || !StringUtils.equals(lease.getExecutionId(), artifact.getExecutionId())
                || !StringUtils.equals(lease.getId(), artifact.getLeaseId())
                || !StringUtils.equals(uploadStatus, artifact.getUploadStatus())
                || !MessageDigest.isEqual(tokenHash.getBytes(StandardCharsets.UTF_8),
                StringUtils.defaultString(artifact.getUploadTokenHash()).getBytes(StandardCharsets.UTF_8))) {
            throw new MSException("ARTIFACT_NOT_FOUND_OR_NOT_ACCESSIBLE");
        }
    }

    private AgentArtifactPrepareResponse preparedResponse(AgentExecutionArtifactDTO artifact, String uploadToken) {
        AgentArtifactPrepareResponse response = new AgentArtifactPrepareResponse();
        response.setArtifactId(artifact.getId());
        response.setUploadPath("/agent/v1/tasks/leases/" + artifact.getLeaseId()
                + "/artifacts/" + artifact.getId() + ":upload");
        response.setUploadToken(uploadToken);
        response.setExpiresAt(artifact.getRetentionUntil());
        response.setStatus(artifact.getUploadStatus());
        return response;
    }

    private String bearer(String leaseToken) {
        if (StringUtils.isBlank(leaseToken)) {
            throw new MSException("AGENT_TASK_LEASE_TOKEN_REQUIRED");
        }
        return "Bearer " + leaseToken.trim();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception ex) {
            throw new MSException("ARTIFACT_READ_FAILED", ex);
        }
    }

    ArtifactType detectArtifact(byte[] bytes, MultipartFile file) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E
                && bytes[3] == 0x47 && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return new ArtifactType("image/png", ".png");
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return new ArtifactType("image/jpeg", ".jpg");
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return new ArtifactType("image/webp", ".webp");
        }
        String declaredType = StringUtils.lowerCase(StringUtils.trimToEmpty(file.getContentType()));
        String originalName = StringUtils.lowerCase(StringUtils.defaultString(file.getOriginalFilename()));
        boolean safeTextType = Set.of("text/plain", "text/csv", "application/json", "application/xml", "text/xml")
                .contains(declaredType);
        boolean safeTextExtension = List.of(".txt", ".log", ".json", ".xml", ".csv")
                .stream().anyMatch(originalName::endsWith);
        if ((safeTextType || safeTextExtension) && isUtf8Text(bytes)) {
            String contentType = "application/json".equals(declaredType) ? "application/json" : "text/plain";
            String extension = originalName.endsWith(".json") ? ".json" : ".txt";
            return new ArtifactType(contentType, extension);
        }
        throw new MSException("ARTIFACT_TYPE_NOT_ALLOWED");
    }

    private boolean isUtf8Text(byte[] bytes) {
        if (bytes.length == 0) return false;
        for (byte value : bytes) {
            if (value == 0) return false;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new MSException("ARTIFACT_HASH_FAILED", ex);
        }
    }

    private AgentExecutionArtifactUploadResponse response(AgentExecutionArtifactDTO artifact) {
        AgentExecutionArtifactUploadResponse response = new AgentExecutionArtifactUploadResponse();
        response.setArtifactId(artifact.getId());
        response.setPurpose(artifact.getPurpose());
        response.setSha256(artifact.getSha256());
        response.setSizeBytes(artifact.getSizeBytes());
        response.setDownloadPath("/ai/execution/task/" + artifact.getTaskId() + "/artifact/" + artifact.getId());
        return response;
    }

    record ArtifactType(String contentType, String extension) {
    }

    private static final class NamedMultipartFile implements MultipartFile {
        private final MultipartFile source;
        private final String fileName;
        private final String contentType;
        private final byte[] bytes;

        private NamedMultipartFile(MultipartFile source, String fileName, String contentType, byte[] bytes) {
            this.source = source;
            this.fileName = Path.of(fileName).getFileName().toString();
            this.contentType = contentType;
            this.bytes = bytes;
        }

        @Override public String getName() { return source.getName(); }
        @Override public String getOriginalFilename() { return fileName; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes.clone(); }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) throws java.io.IOException { java.nio.file.Files.write(dest.toPath(), bytes); }
    }
}
