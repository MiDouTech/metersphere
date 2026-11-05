package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.project.domain.FileMetadata;
import io.metersphere.project.dto.filemanagement.request.FileReUploadRequest;
import io.metersphere.project.dto.filemanagement.request.FileUploadRequest;
import io.metersphere.project.service.FileManagementService;
import io.metersphere.project.service.FileMetadataService;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/** MCP adapter over FileManagement. All mutation barriers use DB locks, including cancellation. */
@Service
@Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class AgentProjectFileService {
    public static final int MAX_CHUNK_BYTES = 1024 * 1024;
    private static final long WINDOW = 86_400_000L;
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentBusinessAccess access;
    @Resource private FileMetadataService files;
    @Resource private FileManagementService management;
    @Resource private AgentExecLogService audit;
    @Value("${metersphere.agent.environment:UNKNOWN}") private String environment;

    public long maxFileBytes() {
        var configured = jdbc.queryForList("SELECT param_value FROM system_parameter WHERE param_key='upload.file.size'", String.class);
        if (configured.size() != 1) throw new MSException("FILE_LIMIT_UNAVAILABLE");
        try {
            long mib = Long.parseLong(configured.getFirst());
            if (mib < 1) throw new NumberFormatException();
            return Math.min(50, mib) * 1024L * 1024;
        } catch (NumberFormatException invalid) {
            throw new MSException("FILE_LIMIT_UNAVAILABLE");
        }
    }

    public Map<String, Object> prepare(Map<String, Object> args) {
        String project = text(args, "projectId");
        boolean updating = args.containsKey("existingFileId");
        authorize(project, updating ? "UPDATE" : "ADD");
        lockProject(project);
        try {
            return prepareAuthorized(args, project, updating);
        } catch (MSException failure) {
            Map<String, Object> proof = new LinkedHashMap<>();
            proof.put("projectId", project);
            if (args.get("requestId") instanceof String request && request.matches("[A-Za-z0-9_.:-]{1,128}")) {
                proof.put("requestId", request);
                var token = AgentTokenContext.get();
                var rows = jdbc.queryForList("SELECT * FROM project_original_upload WHERE project_id=? AND user_id=? AND token_id=? AND request_id=?", project, token.getUserId(), token.getId(), request);
                if (rows.size() == 1) proof.putAll(trustedUploadContext(rows.getFirst()));
            }
            throw auditedFailure(failure, proof);
        }
    }

    private Map<String, Object> prepareAuthorized(Map<String, Object> args, String project, boolean updating) {
        String request = text(args, "requestId");
        if (!request.matches("[A-Za-z0-9_.:-]{1,128}")) throw error("VALIDATION_ERROR:requestId");
        String name = text(args, "fileName");
        ProjectOriginalFileValidator.name(name);
        long size = integer(args, "size");
        if (size <= 0) throw error("SIZE_LIMIT");
        String hash = hash(args, "sha256");
        String module = Objects.toString(args.get("moduleId"), ModuleConstants.DEFAULT_NODE_ID);
        String label = Objects.toString(args.get("sourceLabel"), "");
        if (label.length() > 255 || label.codePoints().anyMatch(Character::isISOControl)) throw error("VALIDATION_ERROR:sourceLabel");
        // Canonical values make field order irrelevant; request IDs remain byte/case sensitive.
        Map<String, Object> frozen = new TreeMap<>(args);
        frozen.put("moduleId", module);
        frozen.put("sourceLabel", label);
        frozen.put("sha256", hash);
        String fingerprint = DigestUtils.sha256Hex(JSON.toJSONString(frozen));
        var token = AgentTokenContext.get();
        var prior = jdbc.queryForList("SELECT * FROM project_original_upload WHERE project_id=? AND user_id=? AND token_id=? AND request_id=?",
                project, token.getUserId(), token.getId(), request);
        if (!prior.isEmpty()) {
            if (!fingerprint.equals(prior.getFirst().get("request_hash"))) throw error("IDEMPOTENCY_CONFLICT");
            return receipt(prior.getFirst());
        }
        if (size > maxFileBytes()) throw error("SIZE_LIMIT");
        management.checkModule(module, ModuleConstants.NODE_TYPE_DEFAULT, project);
        if (updating) {
            if (!Boolean.TRUE.equals(args.get("confirmNewVersion")) || !args.containsKey("expectedVersion")
                    || args.containsKey("reuseVersionId")) throw error("VALIDATION_ERROR:newVersion");
            String expected = immutableVersion(args, "expectedVersion");
            FileMetadata target = exact(project, text(args, "existingFileId"), expected);
            if (!Boolean.TRUE.equals(target.getLatest())) throw error("TARGET_CONFLICT");
            if (!Objects.equals(target.getOriginalName(), name) || !Objects.equals(target.getModuleId(), module)) {
                throw error("VALIDATION_ERROR:newVersionDestination");
            }
        } else if (args.containsKey("expectedVersion") || args.containsKey("confirmNewVersion")) {
            throw error("VALIDATION_ERROR:newVersion");
        }
        var candidates = jdbc.queryForList("SELECT f.ref_id AS fileId,f.id AS versionId FROM project_file_original o JOIN file_metadata f ON f.id=o.version_id "
                + "WHERE o.project_id=? AND o.sha256=? AND o.source_label=? ORDER BY f.id LIMIT 100", project, hash, label);
        String reuse = (String) args.get("reuseVersionId");
        if (reuse != null) {
            if (updating || candidates.stream().noneMatch(c -> reuse.equals(c.get("versionId")))) throw error("VALIDATION_ERROR:reuseVersionId");
            var existing = files.selectById(reuse);
            if (existing == null || existing.getSize() != size) throw error("TARGET_CONFLICT");
        } else if (!updating && !candidates.isEmpty() && !Boolean.TRUE.equals(args.get("saveSeparateOriginal"))) {
            var result = envelope(project, request, null, "REUSE_CONFIRMATION_REQUIRED");
            result.put("candidates", candidates);
            return result;
        }
        Long active = jdbc.queryForObject("SELECT COUNT(*) FROM project_original_upload WHERE project_id=? AND state IN ('PREPARED','UPLOADING') AND expires_at>?", Long.class, project, System.currentTimeMillis());
        if (active != null && active >= 5) throw error("UPLOAD_RATE_LIMIT");
        if (!updating && reuse == null) assertNameAvailable(project, name);
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO project_original_upload(id,project_id,user_id,token_id,request_id,request_hash,request_json,state,byte_size,sha256,version_id,create_time,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, project, token.getUserId(), token.getId(), request, fingerprint, JSON.toJSONString(frozen), reuse == null ? "PREPARED" : "COMPLETE", size, hash, reuse, now, now + WINDOW);
        auditEvent("PROJECT_FILE_PREPARE", id, Map.of("projectId", project, "requestId", request, "sha256", hash));
        return receipt(owned(project, id));
    }

    public Map<String, Object> upload(String action, Map<String, Object> args) {
        String project = text(args, "projectId");
        // Authorize before looking up session ownership, then recheck the frozen operation's specific permission.
        if ("get".equals(action)) authorize(project, "READ");
        else {
            lockToken(project);
            access.project(project, PermissionConstants.PROJECT_FILE_MANAGEMENT_READ, true, AgentTokenScope.PROJECT_FILE_WRITE);
        }
        lockProject(project);
        Map<String, Object> row;
        if (args.containsKey("uploadId")) row = owned(project, text(args, "uploadId"));
        else {
            var token = AgentTokenContext.get();
            var found = jdbc.queryForList("SELECT id FROM project_original_upload WHERE project_id=? AND user_id=? AND token_id=? AND request_id=?",
                    project, token.getUserId(), token.getId(), text(args, "requestId"));
            if (found.size() != 1) throw AgentBusinessAccess.denied();
            row = owned(project, (String) found.getFirst().get("id"));
        }
        Map<String, Object> frozen = JSON.parseMap((String) row.get("request_json"));
        if (args.containsKey("requestId") && !Objects.equals(args.get("requestId"), row.get("request_id"))) throw error("IDEMPOTENCY_CONFLICT");
        if (!"get".equals(action)) authorize(project, frozen.containsKey("existingFileId") ? "UPDATE" : "ADD");
        try {
        if ("get".equals(action)) return receipt(row);
        if ("COMPLETE".equals(row.get("state"))) {
            if ("chunk".equals(action)) {
                byte[] replay = decode(text(args, "base64"));
                long offset = integer(args, "offset");
                String replayHash = hash(args, "chunkHash");
                var chunks = jdbc.queryForList("SELECT byte_size,sha256 FROM project_original_chunk WHERE upload_id=? AND byte_offset=?", row.get("id"), offset);
                if (chunks.size() != 1 || ((Number) chunks.getFirst().get("byte_size")).intValue() != replay.length
                        || !replayHash.equals(DigestUtils.sha256Hex(replay)) || !replayHash.equals(chunks.getFirst().get("sha256"))) {
                    throw error("UPLOAD_CHUNK_CONFLICT");
                }
            }
            return receipt(row);
        }
        if ("cancel".equals(action)) {
            if (!"CANCELLED".equals(row.get("state"))) {
                jdbc.update("UPDATE project_original_upload SET state='CANCELLED' WHERE id=?", row.get("id"));
                jdbc.update("DELETE FROM project_original_chunk WHERE upload_id=?", row.get("id"));
                auditEvent("PROJECT_FILE_CANCEL", (String) row.get("id"), Map.of("projectId", project, "requestId", row.get("request_id")));
            }
            return receipt(owned(project, (String) row.get("id")));
        }
        if (((Number) row.get("expires_at")).longValue() <= System.currentTimeMillis()) throw error("UPLOAD_EXPIRED");
        if (!Set.of("PREPARED", "UPLOADING").contains(row.get("state"))) throw error("UPLOAD_STATE_CONFLICT");
        if ("chunk".equals(action)) {
            byte[] bytes = decode(text(args, "base64"));
            long offset = integer(args, "offset");
            ProjectOriginalFileValidator.range(offset, bytes.length, ((Number) row.get("byte_size")).longValue(), MAX_CHUNK_BYTES);
            String hash = hash(args, "chunkHash");
            if (!hash.equals(DigestUtils.sha256Hex(bytes))) throw error("HASH_MISMATCH");
            var overlaps = jdbc.queryForList("SELECT byte_offset,byte_size,sha256,content FROM project_original_chunk WHERE upload_id=? AND byte_offset<? AND byte_offset+byte_size>?",
                    row.get("id"), offset + bytes.length, offset);
            if (!overlaps.isEmpty()) {
                var prior = overlaps.getFirst();
                if (overlaps.size() != 1 || ((Number) prior.get("byte_offset")).longValue() != offset
                        || !hash.equals(prior.get("sha256")) || !Arrays.equals(bytes, (byte[]) prior.get("content"))) throw error("UPLOAD_CHUNK_CONFLICT");
            } else {
                jdbc.update("INSERT INTO project_original_chunk(upload_id,byte_offset,byte_size,sha256,content) VALUES (?,?,?,?,?)", row.get("id"), offset, bytes.length, hash, bytes);
                jdbc.update("UPDATE project_original_upload SET state='UPLOADING' WHERE id=?", row.get("id"));
            }
            auditEvent("PROJECT_FILE_CHUNK", (String) row.get("id"), Map.of("projectId", project, "requestId", row.get("request_id"), "offset", offset, "length", bytes.length, "chunkHash", hash));
        } else if ("complete".equals(action)) {
            complete(row, frozen);
        } else throw error("VALIDATION_ERROR:action");
        return receipt(owned(project, (String) row.get("id")));
        } catch (MSException failure) {
            throw auditedFailure(failure, trustedUploadContext(row));
        }
    }

    private void complete(Map<String, Object> row, Map<String, Object> frozen) {
        String project = (String) row.get("project_id");
        long size = ((Number) row.get("byte_size")).longValue();
        if (size > maxFileBytes()) throw error("SIZE_LIMIT");
        byte[] bytes = new byte[Math.toIntExact(size)];
        var chunks = jdbc.queryForList("SELECT byte_offset,content FROM project_original_chunk WHERE upload_id=? ORDER BY byte_offset", row.get("id"));
        int position = 0;
        for (var chunk : chunks) {
            byte[] content = (byte[]) chunk.get("content");
            if (((Number) chunk.get("byte_offset")).longValue() != position || content.length > bytes.length - position) throw error("UPLOAD_INCOMPLETE");
            System.arraycopy(content, 0, bytes, position, content.length);
            position += content.length;
        }
        if (position != size) throw error("UPLOAD_INCOMPLETE");
        if (!Objects.equals(row.get("sha256"), DigestUtils.sha256Hex(bytes))) throw error("HASH_MISMATCH");
        String name = text(frozen, "fileName");
        ProjectOriginalFileValidator.container(name, bytes);
        management.checkModule(text(frozen, "moduleId"), ModuleConstants.NODE_TYPE_DEFAULT, project);
        String version;
        try {
            if (frozen.containsKey("existingFileId")) {
                var target = exact(project, text(frozen, "existingFileId"), immutableVersion(frozen, "expectedVersion"));
                if (!Boolean.TRUE.equals(target.getLatest())) throw error("TARGET_CONFLICT");
                FileReUploadRequest request = new FileReUploadRequest();
                request.setFileId(target.getId());
                version = files.reUpload(request, (String) row.get("user_id"), new Original(name, bytes));
            } else {
                assertNameAvailable(project, name);
                FileUploadRequest request = new FileUploadRequest();
                request.setProjectId(project);
                request.setModuleId(text(frozen, "moduleId"));
                version = files.upload(request, (String) row.get("user_id"), new Original(name, bytes));
            }
        } catch (MSException expected) { throw expected; }
        catch (Exception failure) { throw new MSException("FILE_STORAGE_FAILED"); }
        jdbc.update("INSERT INTO project_file_original(version_id,project_id,sha256,source_label,original_name,operation_id,actor_id,stored_at) VALUES (?,?,?,?,?,?,?,?)",
                version, project, row.get("sha256"), frozen.get("sourceLabel"), name, row.get("id"), row.get("user_id"), System.currentTimeMillis());
        jdbc.update("UPDATE project_original_upload SET state='COMPLETE',version_id=? WHERE id=?", version, row.get("id"));
        jdbc.update("UPDATE project_original_chunk SET content=NULL WHERE upload_id=?", row.get("id"));
        auditEvent("PROJECT_FILE_STORED", version, Map.of("projectId", project, "operationId", row.get("id"), "requestId", row.get("request_id"), "sha256", row.get("sha256"), "versionId", version));
    }

    public Map<String, Object> read(String action, Map<String, Object> args) {
        String project = text(args, "projectId");
        authorize(project, "download".equals(action) ? "DOWNLOAD" : "READ");
        var result = envelope(project, null, null, "AVAILABLE");
        if ("search".equals(action)) {
            long page = args.containsKey("current") ? integer(args, "current") : 1;
            long size = args.containsKey("pageSize") ? integer(args, "pageSize") : 20;
            if (page < 1 || page > Integer.MAX_VALUE || size < 1 || size > 100) throw error("VALIDATION_ERROR:page");
            String keyword = Objects.toString(args.get("keyword"), "");
            if (keyword.length() > 255) throw error("VALIDATION_ERROR:keyword");
            String predicate = " FROM file_metadata WHERE project_id=? AND latest=1 AND storage='MINIO' AND LOCATE(?,original_name)>0";
            Long total = jdbc.queryForObject("SELECT COUNT(*)" + predicate, Long.class, project, keyword);
            var ids = jdbc.queryForList("SELECT id" + predicate + " ORDER BY id LIMIT ? OFFSET ?", String.class, project, keyword, size, (page - 1) * size);
            result.put("items", ids.stream().map(id -> metadata(files.selectById(id))).toList());
            result.put("total", total);
            result.put("current", page);
            result.put("pageSize", size);
            return result;
        }
        FileMetadata file = exact(project, text(args, "fileId"), args.containsKey("versionId") ? text(args, "versionId") : "latest");
        if ("versions".equals(action)) {
            var ids = jdbc.queryForList("SELECT id FROM file_metadata WHERE project_id=? AND ref_id=? ORDER BY update_time DESC,id DESC", String.class, project, file.getRefId());
            result.put("items", ids.stream().map(id -> metadata(files.selectById(id))).toList());
        } else if ("download".equals(action)) {
            if (file.getSize() > maxFileBytes()) throw error("SIZE_LIMIT");
            byte[] bytes = files.getFileByte(file);
            long offset = integer(args, "offset");
            long length = integer(args, "length");
            if (offset < 0 || offset > bytes.length || length < 1 || length > MAX_CHUNK_BYTES) throw error("VALIDATION_ERROR:range");
            int end = (int) Math.min(bytes.length, offset + length);
            byte[] part = Arrays.copyOfRange(bytes, (int) offset, end);
            String fullHash = DigestUtils.sha256Hex(bytes);
            var recorded = jdbc.queryForList("SELECT sha256 FROM project_file_original WHERE version_id=?", String.class, file.getId());
            if (!recorded.isEmpty() && !fullHash.equals(recorded.getFirst())) throw error("HASH_MISMATCH");
            result.put("fileRef", metadata(file));
            result.put("offset", offset);
            result.put("length", part.length);
            result.put("base64", Base64.getEncoder().encodeToString(part));
            result.put("chunkHash", DigestUtils.sha256Hex(part));
            result.put("fullHash", fullHash);
            result.put("eof", end == bytes.length);
        } else result.put("fileRef", metadata(file));
        return result;
    }

    /** For import adapters: caller must supply the exact version and retain it in its job. */
    public byte[] readOriginal(String project, String fileId, String versionId, String expectedHash) {
        authorize(project, "DOWNLOAD");
        if ("latest".equals(versionId)) throw error("VALIDATION_ERROR:versionId");
        FileMetadata file = exact(project, fileId, versionId);
        if (file.getSize() > maxFileBytes()) throw error("SIZE_LIMIT");
        byte[] bytes = files.getFileByte(file);
        if (!DigestUtils.sha256Hex(bytes).equals(expectedHash)) throw error("HASH_MISMATCH");
        return bytes;
    }

    private FileMetadata exact(String project, String fileId, String version) {
        FileMetadata identity = files.selectById(fileId);
        if (identity == null || !project.equals(identity.getProjectId()) || !"MINIO".equals(identity.getStorage())) throw AgentBusinessAccess.denied();
        String resolved = version;
        if ("latest".equals(version)) {
            var ids = jdbc.queryForList("SELECT id FROM file_metadata WHERE project_id=? AND ref_id=? AND latest=1", String.class, project, identity.getRefId());
            if (ids.size() != 1) throw error("TARGET_CONFLICT");
            resolved = ids.getFirst();
        }
        FileMetadata found = files.selectById(resolved);
        if (found == null || !project.equals(found.getProjectId()) || !identity.getRefId().equals(found.getRefId())) throw AgentBusinessAccess.denied();
        return found;
    }

    private void authorize(String project, String mode) {
        lockToken(project);
        String scope = "DOWNLOAD".equals(mode) ? AgentTokenScope.PROJECT_FILE_DOWNLOAD
                : "READ".equals(mode) ? AgentTokenScope.PROJECT_FILE_READ : AgentTokenScope.PROJECT_FILE_WRITE;
        access.project(project, PermissionConstants.PROJECT_FILE_MANAGEMENT_READ, !"READ".equals(mode) && !"DOWNLOAD".equals(mode), scope);
        if (!"READ".equals(mode)) access.project(project, "PROJECT_FILE_MANAGEMENT:READ+" + mode, !"DOWNLOAD".equals(mode), scope);
    }

    private void lockProject(String project) {
        jdbc.queryForList("SELECT id FROM project WHERE id=? FOR UPDATE", project);
    }

    private void lockToken(String project) {
        var token = AgentTokenContext.get();
        if (token == null) throw AgentBusinessAccess.denied();
        jdbc.queryForList("SELECT id FROM agent_token WHERE id=? FOR UPDATE", token.getId());
        access.lockProjectFileAuthorization(token.getUserId(), project);
    }

    private void auditEvent(String action, String resource, Map<String, Object> values) {
        var details = new LinkedHashMap<>(values);
        var token = AgentTokenContext.get();
        details.put("actorId", token == null ? null : token.getUserId());
        details.put("tokenId", token == null ? null : token.getId());
        details.put("environment", environment);
        details.put("stage", "PROJECT_FILE");
        details.putIfAbsent("traceId", org.slf4j.MDC.get("traceId"));
        details.put("result", "PROJECT_FILE_REJECT".equals(action) ? "REJECTED" : "ACCEPTED");
        audit.audit(action, resource, JSON.toJSONString(details));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordRejection(MSException failure, String action, Map<String, Object> args, String traceId) {
        String code = failure.getMessage();
        String safeCode = code != null && code.matches("[A-Z][A-Z0-9_]{2,63}") ? code : "VALIDATION_ERROR";
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("errorCode", safeCode);
        context.put("action", action);
        context.put("traceId", traceId);
        if (failure instanceof ProjectFileException file && !file.auditContext().isEmpty()) {
            // Proof was created only after successful current authorization/ownership checks,
            // inside the failed transaction. Keep it even if permissions are revoked after rollback.
            context.putAll(file.auditContext());
            auditEvent("PROJECT_FILE_REJECT", Objects.toString(context.get("operationId"), "AUDIT"), context);
            return;
        }
        String resource = "AUDIT";
        var token = AgentTokenContext.get();
        if (token != null && args.get("projectId") instanceof String project && project.length() <= 128) {
            try {
                // Re-resolve trusted ownership after the failed transaction rolled back. Never copy
                // a submitted hash/name/version into an audit event, or reveal inaccessible resources.
                access.project(project, PermissionConstants.PROJECT_FILE_MANAGEMENT_READ, false,
                        AgentTokenScope.PROJECT_FILE_READ, AgentTokenScope.PROJECT_FILE_WRITE, AgentTokenScope.PROJECT_FILE_DOWNLOAD);
                context.put("projectId", project);
                List<Map<String, Object>> rows = List.of();
                if (args.get("uploadId") instanceof String id && id.length() <= 128) {
                    rows = jdbc.queryForList("SELECT * FROM project_original_upload WHERE id=? AND project_id=? AND user_id=? AND token_id=?", id, project, token.getUserId(), token.getId());
                } else if (args.get("requestId") instanceof String request && request.matches("[A-Za-z0-9_.:-]{1,128}")) {
                    rows = jdbc.queryForList("SELECT * FROM project_original_upload WHERE request_id=? AND project_id=? AND user_id=? AND token_id=?", request, project, token.getUserId(), token.getId());
                }
                if (rows.size() == 1) {
                    var row = rows.getFirst();
                    resource = (String) row.get("id");
                    context.putAll(trustedUploadContext(row));
                }
            } catch (MSException denied) {
                // Anonymous rejection keeps actor/trace only. No existence or project metadata.
                context.remove("projectId");
            }
        }
        auditEvent("PROJECT_FILE_REJECT", resource, context);
    }

    private Map<String, Object> trustedUploadContext(Map<String, Object> row) {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("projectId", row.get("project_id"));
        proof.put("operationId", row.get("id"));
        proof.put("uploadId", row.get("id"));
        proof.put("requestId", row.get("request_id"));
        proof.put("sha256", row.get("sha256"));
        if (row.get("version_id") != null) proof.put("versionId", row.get("version_id"));
        Map<String, Object> frozen = JSON.parseMap((String) row.get("request_json"));
        if (frozen.get("existingFileId") instanceof String fileId) proof.put("fileId", fileId);
        if (frozen.get("expectedVersion") instanceof String expected && !"latest".equalsIgnoreCase(expected)) proof.put("expectedVersion", expected);
        return proof;
    }

    private ProjectFileException auditedFailure(MSException failure, Map<String, Object> proof) {
        var error = failure instanceof ProjectFileException file ? file : new ProjectFileException(failure);
        return error.withAuditContext(proof);
    }

    private Map<String, Object> owned(String project, String id) {
        var token = AgentTokenContext.get();
        var rows = jdbc.queryForList("SELECT * FROM project_original_upload WHERE id=? AND project_id=? AND user_id=? AND token_id=? FOR UPDATE", id, project, token.getUserId(), token.getId());
        if (rows.size() != 1) throw AgentBusinessAccess.denied();
        return rows.getFirst();
    }

    private void assertNameAvailable(String project, String name) {
        int dot = name.lastIndexOf('.');
        var ids = jdbc.queryForList("SELECT id FROM file_metadata WHERE project_id=? AND latest=1 AND name=? AND type=? AND storage='MINIO' ORDER BY id LIMIT 100", String.class, project, name.substring(0, dot), name.substring(dot + 1));
        if (!ids.isEmpty()) throw new ProjectFileException("FILE_NAME_CONFLICT", Map.of("candidates", ids.stream().map(id -> metadata(files.selectById(id))).toList()));
    }

    private Map<String, Object> receipt(Map<String, Object> row) {
        String state = (String) row.get("state");
        if (Set.of("PREPARED", "UPLOADING").contains(state) && ((Number) row.get("expires_at")).longValue() <= System.currentTimeMillis()) state = "EXPIRED";
        var result = envelope((String) row.get("project_id"), (String) row.get("request_id"), (String) row.get("id"), state);
        result.put("uploadId", row.get("id"));
        result.put("fileState", "COMPLETE".equals(state) ? "STORED" : state);
        result.put("importState", "NOT_REQUESTED");
        result.put("expiresAt", row.get("expires_at"));
        result.put("maxChunkBytes", MAX_CHUNK_BYTES);
        result.put("receivedRanges", jdbc.queryForList("SELECT byte_offset AS `offset`,byte_size AS `length`,sha256 AS chunkHash FROM project_original_chunk WHERE upload_id=? ORDER BY byte_offset", row.get("id")));
        if (row.get("version_id") != null) {
            var file = files.selectById((String) row.get("version_id"));
            result.put("fileRef", file == null ? Map.of("versionId", row.get("version_id"), "currentState", "RESOURCE_GONE") : metadata(file));
        }
        return result;
    }

    private Map<String, Object> metadata(FileMetadata file) {
        if (file == null) throw error("RESOURCE_GONE");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", file.getProjectId());
        result.put("fileId", file.getRefId());
        result.put("versionId", file.getId());
        result.put("systemVersion", file.getFileVersion());
        result.put("originalName", file.getOriginalName());
        result.put("byteSize", file.getSize());
        result.put("moduleId", file.getModuleId());
        result.put("latest", file.getLatest());
        result.put("uploadedBy", file.getUpdateUser());
        result.put("uploadedAt", file.getUpdateTime());
        var original = jdbc.queryForList("SELECT sha256,source_label AS sourceLabel,operation_id AS operationId FROM project_file_original WHERE version_id=?", file.getId());
        if (!original.isEmpty()) result.putAll(original.getFirst());
        else result.put("hashState", "NOT_RECORDED");
        result.put("webPath", "/#/project-management/fileManagement?pId=" + java.net.URLEncoder.encode(file.getProjectId(), java.nio.charset.StandardCharsets.UTF_8));
        return result;
    }

    private Map<String, Object> envelope(String project, String request, String operation, String state) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("environment", environment);
        result.put("projectId", project);
        result.put("requestId", request);
        result.put("operationId", operation);
        result.put("state", state);
        result.put("traceId", org.slf4j.MDC.get("traceId"));
        result.put("retryable", !Set.of("COMPLETE", "CANCELLED", "EXPIRED").contains(state));
        result.put("nextAction", "COMPLETE".equals(state) ? "READ_EXACT_VERSION" : "QUERY_ORIGINAL_REQUEST");
        return result;
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void expireChunks() {
        // Delete temporary bytes only. Completed IDs, hashes and request fingerprints survive.
        long now = System.currentTimeMillis();
        var expired = jdbc.queryForList("SELECT id,project_id FROM project_original_upload WHERE expires_at<? AND state IN ('PREPARED','UPLOADING') ORDER BY project_id,id LIMIT 100", now);
        for (var upload : expired) {
            lockProject((String) upload.get("project_id"));
            var locked = jdbc.queryForList("SELECT state FROM project_original_upload WHERE id=? FOR UPDATE", upload.get("id"));
            if (locked.size() == 1 && Set.of("PREPARED", "UPLOADING").contains(locked.getFirst().get("state"))) {
                jdbc.update("DELETE FROM project_original_chunk WHERE upload_id=?", upload.get("id"));
                jdbc.update("UPDATE project_original_upload SET state='EXPIRED' WHERE id=?", upload.get("id"));
            }
        }
    }

    public static String text(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw error("VALIDATION_ERROR:" + key);
        return text;
    }
    private static String immutableVersion(Map<String, Object> args, String key) {
        String value = text(args, key);
        if ("latest".equalsIgnoreCase(value)) throw error("VALIDATION_ERROR:" + key);
        return value;
    }
    public static long integer(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != number.longValue()) throw error("VALIDATION_ERROR:" + key);
        return number.longValue();
    }
    private static String hash(Map<String, Object> args, String key) {
        String value = text(args, key);
        if (!value.matches("[a-fA-F0-9]{64}")) throw error("VALIDATION_ERROR:" + key);
        return value.toLowerCase(Locale.ROOT);
    }
    private static byte[] decode(String encoded) {
        if (encoded.length() > 4 * ((MAX_CHUNK_BYTES + 2) / 3)) throw error("SIZE_LIMIT:chunk");
        try { return Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException invalid) { throw error("VALIDATION_ERROR:base64"); }
    }
    private static MSException error(String code) { return new ProjectFileException(code); }

    private record Original(String name, byte[] bytes) implements MultipartFile {
        public String getName() { return "file"; }
        public String getOriginalFilename() { return name; }
        public String getContentType() { return "application/octet-stream"; }
        public boolean isEmpty() { return bytes.length == 0; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes; }
        public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        public void transferTo(java.io.File file) throws java.io.IOException { java.nio.file.Files.write(file.toPath(), bytes); }
    }
}
