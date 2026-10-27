package io.metersphere.functional.service;

import io.metersphere.functional.dto.ExportTaskDTO;
import io.metersphere.functional.request.FunctionalCaseExportRequest;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.service.UserLoginService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Durable lifecycle for CASE exports. Other export types retain their original protocol. */
@Service
public class FunctionalCaseExportTaskService {
    @Resource private JdbcTemplate jdbc;
    @Resource private io.metersphere.functional.asset.service.CaseAssetExportAccess assetAccess;
    @Resource private ProjectMapper projectMapper;
    @Resource private UserLoginService userLoginService;
    @Resource private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Resource private io.metersphere.system.service.FileService fileService;
    @Value("${case.export.timeout-ms:1800000}") private long timeoutMs = 1_800_000;
    @Value("${case.export.retention-ms:86400000}") private long retentionMs = 86_400_000;
    @Value("${case.export.per-user-inflight:2}") private int perUserInflight = 2;
    @Value("${case.export.global-inflight:18}") private int globalInflight = 18;
    private final String workerId = UUID.randomUUID().toString();
    private final ConcurrentMap<String, FutureTask<Void>> running = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;

    public FunctionalCaseExportTaskService() { this(2, 16); }

    @org.springframework.beans.factory.annotation.Autowired
    public FunctionalCaseExportTaskService(@Value("${case.export.workers:2}") int workers,
                                            @Value("${case.export.queue-size:16}") int queueSize) {
        if (workers < 1 || workers > 16 || queueSize < 1 || queueSize > 256) {
            throw new IllegalArgumentException("Invalid case export pool limits");
        }
        executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize), runnable -> {
                    Thread thread = new Thread(runnable, "case-export"); thread.setDaemon(true); return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @jakarta.annotation.PostConstruct
    public void validateConfiguration() {
        if (timeoutMs < 1000 || retentionMs < 1000 || perUserInflight < 1 || globalInflight < perUserInflight) throw new IllegalArgumentException("Invalid case export lifetime");
    }

    public record Result(String fileType, int count) { }
    public record RetryParameters(FunctionalCaseExportRequest request, String format) { }

    public ExportTaskDTO existingRetry(String sourceId, String userId, String requestId) {
        ExportTaskDTO original = get(sourceId, userId);
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,64}")) throw new MSException("重试请求标识无效");
        var rows = jdbc.queryForList("SELECT id,source_task_id FROM export_task WHERE project_id=? AND create_user=? AND type='CASE' AND request_key=?",
                original.getProjectId(), userId, requestId);
        if (rows.isEmpty()) return null;
        if (!Objects.equals(sourceId, rows.getFirst().get("source_task_id")))
            throw new MSException("相同请求标识已用于其他导出，请使用新的请求标识");
        return get((String) rows.getFirst().get("id"), userId);
    }

    public RetryParameters prepareRetry(String sourceId, String userId, String requestId) {
        ExportTaskDTO original = get(sourceId, userId);
        if (!Set.of("FAILED", "CANCELLED", "EXPIRED").contains(original.getState()))
            throw new MSException("只能重试失败、已停止或已过期的导出任务");
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,64}"))
            throw new MSException("重试请求标识无效");
        String payload = jdbc.queryForObject("SELECT request_payload FROM export_task WHERE id=? AND create_user=? AND type='CASE'",
                String.class, sourceId, userId);
        if (StringUtils.isBlank(payload)) throw new MSException("旧任务没有完整导出参数，请重新选择用例导出");
        FunctionalCaseExportRequest request;
        try { request = JSON.parseObject(payload, FunctionalCaseExportRequest.class); }
        catch (Exception invalid) { throw new MSException("原任务参数无法恢复，请重新选择用例导出"); }
        if (request == null || !Objects.equals(original.getProjectId(), request.getProjectId()))
            throw new MSException("原任务参数无法恢复，请重新选择用例导出");
        String format = original.getFileType();
        if ("zip".equals(format)) format = "xlsx";
        if (!Set.of("xlsx", "xmind").contains(Objects.toString(format, "")))
            throw new MSException("原任务格式无法恢复，请重新选择用例导出");
        request.setRequestId(requestId);
        request.setFileId(null);
        request.setSourceTaskId(sourceId);
        return new RetryParameters(request, format);
    }

    public void authorize(String projectId, String userId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(projectId)) throw denied();
        var project = projectMapper.selectByPrimaryKey(projectId);
        if (project == null || !Boolean.TRUE.equals(project.getEnable())) throw denied();
        try {
            var user = userLoginService.getUserDTO(userId);
            if (user == null || !SessionUtils.hasPermission(SessionUser.fromUser(user, ""),
                    project.getOrganizationId(), projectId, PermissionConstants.FUNCTIONAL_CASE_READ_EXPORT)) {
                throw denied();
            }
        } catch (Exception exception) {
            throw denied();
        }
    }

    public void authorizeRequest(FunctionalCaseExportRequest request,String userId) {
        if(StringUtils.isNotBlank(request.getAssetCatalogId()))assetAccess.authorize(request,userId);
        else authorize(request.getProjectId(),userId);
    }
    private void authorizeRow(Map<String,Object> row,String userId) {
        String raw=(String)row.get("request_json");
        FunctionalCaseExportRequest request=StringUtils.isBlank(raw)?null:JSON.parseObject(raw,FunctionalCaseExportRequest.class);
        if(request!=null && StringUtils.isNotBlank(request.getAssetCatalogId()))authorizeRequest(request,userId);
        else authorize((String)row.get("project_id"),userId);
    }

    private MSException denied() { return new MSException("导出任务不存在或当前账号无权访问"); }

    public String start(FunctionalCaseExportRequest request, String userId, String format,
                        Function<String, Result> generate) {
        return start(request, userId, format, taskId -> null, (taskId, ignored) -> generate.apply(taskId));
    }

    public <T> String start(FunctionalCaseExportRequest request, String userId, String format,
                           Function<String, T> freeze, java.util.function.BiFunction<String, T, Result> generate) {
        authorizeRequest(request, userId);
        String key = StringUtils.defaultIfBlank(request.getRequestId(), UUID.randomUUID().toString());
        if (!key.matches("[A-Za-z0-9_-]{1,64}")) throw new MSException("导出请求标识无效");
        // Client file IDs are deliberately excluded; only the server allocates storage names.
        request.setRequestId(key);
        request.setFileId(null);
        String payload = JSON.toJSONString(request);
        String hash = DigestUtils.sha256Hex(format + "\n" + payload);
        var existing = jdbc.queryForList("SELECT id,request_hash FROM export_task WHERE create_user=? AND project_id=? AND type='CASE' AND request_key=?",
                userId, request.getProjectId(), key);
        if (!existing.isEmpty()) return replay(existing.getFirst(), hash);
        String id = UUID.randomUUID().toString();
        String fileId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        var reservation = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        String reservedId = reservation.execute(status -> {
            jdbc.queryForObject("SELECT type FROM export_task_capacity WHERE type='CASE' FOR UPDATE", String.class);
            var concurrent = jdbc.queryForList("SELECT id,request_hash FROM export_task WHERE create_user=? AND project_id=? AND type='CASE' AND request_key=?",
                    userId, request.getProjectId(), key);
            if (!concurrent.isEmpty()) return replay(concurrent.getFirst(), hash);
            Integer own = jdbc.queryForObject("SELECT COUNT(*) FROM export_task WHERE type='CASE' AND state='PREPARED' AND create_user=?", Integer.class, userId);
            Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM export_task WHERE type='CASE' AND state='PREPARED'", Integer.class);
            if (own >= perUserInflight || total >= globalInflight) throw new MSException("导出任务较多，请等待现有任务结束后重试");
            jdbc.update("""
                    INSERT INTO export_task(id,type,file_id,file_type,project_id,state,create_user,create_time,
                    update_user,update_time,request_key,request_hash,request_payload,worker_id,lease_until,source_task_id)
                    VALUES (?,'CASE',?,?,?,'PREPARED',?,?,?,?,?,?,?,?,?,?)
                    """, id, fileId, format, request.getProjectId(), userId, now, userId, now,
                    key, hash, payload, workerId, now + timeoutMs, request.getSourceTaskId());
            return id;
        });
        if (!id.equals(reservedId)) return reservedId;
        request.setFileId(fileId);
        final T snapshot;
        try {
            authorizeRequest(request, userId);
            checkActive(id);
            snapshot = freeze.apply(id);
            checkActive(id);
        } catch (Exception exception) {
            fail(id, exception instanceof EmptyExportException ? "EXPORT_EMPTY" : "EXPORT_FAILED");
            notifyLegacy(fileId, id, 0, false);
            LogUtils.error("Case export snapshot failed, taskId=" + id, exception);
            return id;
        }
        FutureTask<Void> future = new FutureTask<>(() -> {
            try {
                authorizeRequest(request, userId);
                if (!active(id)) return null;
                Result result = generate.apply(id, snapshot);
                authorizeRequest(request, userId);
                assertSnapshotScope(id, request.getProjectId());
                long finished = System.currentTimeMillis();
                int published = jdbc.update("""
                        UPDATE export_task SET state='SUCCESS',file_type=?,result_count=?,finished_at=?,
                        expires_at=?,update_time=?,state_version=state_version+1
                        WHERE id=? AND state='PREPARED' AND worker_id=? AND lease_until>?
                        """, result.fileType(), result.count(), finished, finished + retentionMs, finished, id, workerId, finished);
                if (published == 0) removeFile(fileId, result.fileType());
                else notifyLegacy(fileId, id, result.count(), true);
            } catch (Exception exception) {
                fail(id, exception instanceof EmptyExportException ? "EXPORT_EMPTY" : "EXPORT_FAILED");
                notifyLegacy(fileId, id, 0, false);
                LogUtils.error("Case export failed, taskId=" + id, exception);
            } finally {
                // A non-interruptible upload can finish after the lease sweeper has already cleaned this task.
                // Re-register cleanup even when publishing lost the terminal-state race or generation threw.
                try {
                    jdbc.update("UPDATE export_task SET cleanup_done=0 WHERE id=? AND state IN ('STOP','ERROR')", id);
                } catch (Exception cleanupRegistrationFailure) {
                    LogUtils.error("Late export cleanup registration failed, taskId=" + id, cleanupRegistrationFailure);
                }
                running.remove(id);
            }
            return null;
        });
        running.put(id, future);
        try {
            executor.execute(future);
        } catch (RejectedExecutionException rejected) {
            running.remove(id);
            fail(id, "EXPORT_BUSY");
        }
        return id;
    }

    private void notifyLegacy(String fileId, String taskId, int count, boolean success) {
        // Best effort for old browser clients; persistence and new polling do not depend on this path.
        try {
            io.metersphere.system.socket.ExportWebSocketHandler.sendMessageSingle(new io.metersphere.sdk.dto.ExportMsgDTO(
                    fileId, taskId, count, success, io.metersphere.sdk.constants.MsgType.EXEC_RESULT.name()));
        } catch (Exception notificationFailure) {
            LogUtils.error("Export notification failed, taskId=" + taskId, notificationFailure);
        }
    }

    private String replay(Map<String, Object> row, String hash) {
        if (!hash.equals(row.get("request_hash"))) throw new MSException("相同导出请求标识对应不同参数，请重新发起导出");
        return (String) row.get("id");
    }

    public static class EmptyExportException extends RuntimeException { }

    private boolean active(String id) {
        return !Thread.currentThread().isInterrupted() && Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT COUNT(*) FROM export_task WHERE id=? AND state='PREPARED' AND worker_id=? AND lease_until>?",
                Integer.class, id, workerId, System.currentTimeMillis()));
    }

    public void checkActive(String id) {
        if (!active(id)) throw new MSException("导出任务已停止或超时");
    }

    private void fail(String id, String code) {
        jdbc.update("UPDATE export_task SET state='ERROR',safe_error_code=?,finished_at=?,update_time=?,state_version=state_version+1 WHERE id=? AND state='PREPARED'",
                code, System.currentTimeMillis(), System.currentTimeMillis(), id);
    }

    public ExportTaskDTO get(String id, String userId) {
        var rows = jdbc.queryForList("SELECT * FROM export_task WHERE id=? AND create_user=? AND type='CASE'", id, userId);
        if (rows.isEmpty()) throw denied();
        var row = rows.getFirst();
        authorizeRow(row,userId);
        return toDTO(row);
    }

    public List<ExportTaskDTO> recent(String projectId, String userId) {
        authorize(projectId, userId);
        return jdbc.queryForList("SELECT * FROM export_task WHERE project_id=? AND create_user=? AND type='CASE' ORDER BY (state='PREPARED') DESC,create_time DESC,id DESC LIMIT 20",
                projectId, userId).stream().map(this::toDTO).toList();
    }

    public io.metersphere.system.utils.Pager<List<ExportTaskDTO>> history(String projectId, String userId, int current, int pageSize) {
        authorize(projectId, userId);
        if (current < 1 || pageSize < 1 || pageSize > 100) throw new MSException("导出记录分页参数不合法");
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM export_task WHERE project_id=? AND create_user=? AND type='CASE'",
                Long.class, projectId, userId);
        long count = total == null ? 0 : total;
        int page = (int) Math.min(current, Math.max(1, (count + pageSize - 1) / pageSize));
        List<ExportTaskDTO> rows = count == 0 ? List.of() : jdbc.queryForList(
                "SELECT * FROM export_task WHERE project_id=? AND create_user=? AND type='CASE' ORDER BY create_time DESC,id DESC LIMIT ?,?",
                projectId, userId, (long) (page - 1) * pageSize, pageSize).stream().map(this::toDTO).toList();
        return new io.metersphere.system.utils.Pager<>(rows, count, pageSize, page);
    }

    public ExportTaskDTO stop(String id, String userId) {
        get(id, userId);
        jdbc.update("UPDATE export_task SET state='STOP',finished_at=?,update_time=?,state_version=state_version+1 WHERE id=? AND create_user=? AND type='CASE' AND state='PREPARED'",
                System.currentTimeMillis(), System.currentTimeMillis(), id, userId);
        Future<?> future = running.get(id);
        if (future != null) future.cancel(true);
        return get(id, userId);
    }

    public ExportTaskDTO forDownload(String projectId, String fileId, String userId) {
        var rows = jdbc.queryForList("SELECT * FROM export_task WHERE project_id=? AND file_id=? AND create_user=? AND type='CASE' ORDER BY create_time DESC LIMIT 1",
                projectId, fileId, userId);
        if (rows.isEmpty()) throw denied();
        authorizeRow(rows.getFirst(),userId);
        ExportTaskDTO task = toDTO(rows.getFirst());
        assertSnapshotScope(task.getTaskId(), projectId);
        if (!"SUCCEEDED".equals(task.getState())) throw new MSException("导出文件未就绪或已过期，请重新导出");
        return task;
    }

    private ExportTaskDTO toDTO(Map<String, Object> row) {
        var dto = new ExportTaskDTO();
        dto.setTaskId((String) row.get("id"));
        dto.setSourceTaskId((String) row.get("source_task_id"));
        dto.setTraceId(dto.getTaskId());
        dto.setFileId((String) row.get("file_id"));
        dto.setProjectId((String) row.get("project_id"));
        dto.setName((String) row.get("name"));
        if (row.get("create_time") instanceof Number created) dto.setCreateTime(created.longValue());
        if (row.get("finished_at") instanceof Number finished) dto.setFinishedAt(finished.longValue());
        dto.setFileType((String) row.get("file_type"));
        dto.setCount(((Number) row.get("result_count")).intValue());
        dto.setStateVersion(((Number) row.get("state_version")).longValue());
        if (row.get("expires_at") instanceof Number expiry) dto.setExpiresAt(expiry.longValue());
        dto.setState(switch ((String) row.get("state")) {
            case "SUCCESS" -> dto.getExpiresAt() != null && dto.getExpiresAt() <= System.currentTimeMillis() ? "EXPIRED" : "SUCCEEDED";
            case "STOP" -> "CANCELLED";
            case "ERROR" -> "FAILED";
            default -> "RUNNING";
        });
        dto.setErrorCode((String) row.get("safe_error_code"));
        dto.setMessage(switch (StringUtils.defaultString(dto.getErrorCode())) {
            case "EXPORT_EMPTY" -> "没有可导出的用例，请调整选择范围";
            case "EXPORT_BUSY" -> "导出任务较多，请稍后重新导出";
            case "EXPORT_TIMEOUT" -> "导出超时或服务已重启，请重新导出";
            default -> "FAILED".equals(dto.getState()) ? "导出失败，请重新导出并提供任务编号以便排查" : null;
        });
        return dto;
    }

    public <T> T readSnapshot(java.util.function.Supplier<T> reader) {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(60);
        return transaction.execute(status -> reader.get());
    }

    public void saveSnapshot(String id, List<String> ids, Object content) {
        String snapshot = JSON.toJSONString(content);
        if (snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8 * 1024 * 1024) {
            throw new MSException("导出内容过大，请缩小范围分批导出");
        }
        if (jdbc.update("UPDATE export_task SET content_snapshot=?,snapshot_ids=?,state_version=state_version+1 WHERE id=? AND state='PREPARED' AND worker_id=? AND lease_until>?",
                snapshot, JSON.toJSONString(ids), id, workerId, System.currentTimeMillis()) != 1) {
            throw new MSException("导出任务已停止或超时");
        }
    }

    private void assertSnapshotScope(String id, String projectId) {
        String raw = jdbc.queryForObject("SELECT snapshot_ids FROM export_task WHERE id=?", String.class, id);
        if (raw == null) return; // pre-migration exports have no recoverable content snapshot
        List<String> ids = JSON.parseArray(raw, String.class);
        String requestJson=jdbc.queryForObject("SELECT request_json FROM export_task WHERE id=?",String.class,id);
        if(StringUtils.isNotBlank(requestJson)) {
            var request=JSON.parseObject(requestJson,FunctionalCaseExportRequest.class);
            if(StringUtils.isNotBlank(request.getAssetCatalogId()))assetAccess.validateIds(request.getAssetCatalogId(),ids);
        }
        for (int offset = 0; offset < ids.size(); offset += 1000) {
            List<String> batch = ids.subList(offset, Math.min(offset + 1000, ids.size()));
            List<Object> args = new ArrayList<>(batch);
            args.add(projectId);
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM functional_case WHERE id IN (" +
                    String.join(",", Collections.nCopies(batch.size(), "?")) + ") AND project_id=? AND deleted=0", Integer.class, args.toArray());
            if (!Integer.valueOf(batch.size()).equals(count)) throw new MSException("导出范围已失效，请重新导出");
        }
    }

    private boolean removeFile(String fileId, String type) {
        if (fileService == null) return false;
        var request = new io.metersphere.sdk.file.FileRequest();
        request.setFileName(fileId + "." + type);
        request.setFolder(io.metersphere.sdk.constants.DefaultRepositoryDir.getExportExcelTempDir());
        request.setStorage(io.metersphere.sdk.constants.StorageType.MINIO.name());
        try { fileService.deleteFile(request); return true; }
        catch (Exception error) { LogUtils.error("Export file cleanup will be retried: " + fileId, error); return false; }
    }

    @Scheduled(fixedDelay = 10000)
    public void recover() {
        jdbc.update("UPDATE export_task SET state='ERROR',safe_error_code='EXPORT_TIMEOUT',finished_at=?,state_version=state_version+1 WHERE type='CASE' AND state='PREPARED' AND lease_until IS NOT NULL AND lease_until<=?",
                System.currentTimeMillis(), System.currentTimeMillis());
        jdbc.update("UPDATE export_task SET state='ERROR',safe_error_code='EXPORT_TIMEOUT',finished_at=?,state_version=state_version+1 WHERE type='CASE' AND state='PREPARED' AND lease_until IS NULL AND create_time<?",
                System.currentTimeMillis(), System.currentTimeMillis() - timeoutMs);
        running.forEach((id, future) -> {
            if (!active(id)) { future.cancel(true); running.remove(id, future); }
        });
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanupFiles() {
        // Never clean a still-running cancelled writer until its execution deadline has elapsed.
        long now = System.currentTimeMillis();
        for (var row : jdbc.queryForList("SELECT id,file_id,file_type FROM export_task WHERE type='CASE' AND request_key IS NOT NULL AND cleanup_done=0 AND ((state='SUCCESS' AND expires_at<=?) OR (state IN ('STOP','ERROR') AND lease_until<=?)) LIMIT 50", now, now)) {
            if (removeFile((String) row.get("file_id"), (String) row.get("file_type"))) {
                jdbc.update("UPDATE export_task SET cleanup_done=1,content_snapshot=NULL WHERE id=?", row.get("id"));
            }
        }
    }

    public void registerFile(String taskId, String fileType) {
        if (jdbc.update("UPDATE export_task SET file_type=? WHERE id=? AND state='PREPARED' AND worker_id=? AND lease_until>?",
                fileType, taskId, workerId, System.currentTimeMillis()) != 1) throw new MSException("导出任务已停止或超时");
    }

    @PreDestroy
    public void close() { executor.shutdownNow(); }
}
