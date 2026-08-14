package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.dto.AgentExecutionArtifactUploadResponse;
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

import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentExecutionArtifactService {
    private static final long MAX_SCREENSHOT_BYTES = 5L * 1024L * 1024L;
    private static final long DEFAULT_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L;
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
        if (file == null || file.isEmpty() || file.getSize() > MAX_SCREENSHOT_BYTES) {
            throw new MSException("ARTIFACT_SIZE_INVALID");
        }
        byte[] bytes = readBytes(file);
        ArtifactType artifactType = detectArtifact(bytes, file);
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
        artifact.setRetentionUntil(now + DEFAULT_RETENTION_MS);
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
