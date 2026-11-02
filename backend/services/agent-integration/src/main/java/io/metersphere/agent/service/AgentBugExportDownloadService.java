package io.metersphere.agent.service;

import io.metersphere.sdk.constants.DefaultRepositoryDir;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.FileCenter;
import io.metersphere.sdk.file.FileRequest;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

/** Authenticated bounded transfer. No payload or storage locator is accepted from clients. */
@Service
public class AgentBugExportDownloadService {
    @Resource private AgentTempAttachmentService attachments;

    public Map<String, Object> download(String projectId, String fileId, long offset, int length) {
        if (offset < 0 || length < 1 || length > 65536) throw new MSException("AGENT_VALIDATION_ERROR");
        var metadata = attachments.requireBugExportDownload(projectId, fileId);
        if (!(metadata.get("size") instanceof Number size) || size.longValue() < 0
                || size.longValue() > AgentBugTransferService.MAX_BYTES || offset > size.longValue()) {
            throw new MSException("AGENT_VALIDATION_ERROR");
        }
        var request = new FileRequest();
        request.setFolder(DefaultRepositoryDir.getSystemTempDir() + "/" + fileId);
        request.setFileName((String) metadata.get("file_name"));
        try (var stream = open(request)) {
            var result = new LinkedHashMap<>(readChunk(stream, size.longValue(), offset, length));
            result.put("fileId", fileId);
            result.put("contentType", Objects.toString(metadata.get("content_type"), "application/octet-stream"));
            return result;
        } catch (MSException business) {
            throw business;
        } catch (Exception failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Bug export file read failed fileId={}", fileId, failure);
            throw new MSException("BUG_EXPORT_DOWNLOAD_FAILED");
        }
    }

    protected InputStream open(FileRequest request) throws Exception {
        return FileCenter.getDefaultRepository().getFileAsStream(request);
    }

    static Map<String, Object> readChunk(InputStream stream, long size, long offset, int length) throws Exception {
        if (size < 0 || size > AgentBugTransferService.MAX_BYTES || offset < 0 || offset > size
                || length < 1 || length > 65536) throw new MSException("AGENT_VALIDATION_ERROR");
        var digest = MessageDigest.getInstance("SHA-256");
        // Scan with a fixed buffer to compute a whole-file digest without materializing the file.
        byte[] buffer = new byte[8192];
        byte[] chunk = new byte[(int) Math.min(length, size - offset)];
        long position = 0;
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (count == 0) continue;
            if (position + count > size) throw new MSException("BUG_EXPORT_FILE_CHANGED");
            digest.update(buffer, 0, count);
            long start = Math.max(position, offset), end = Math.min(position + count, offset + chunk.length);
            if (end > start) System.arraycopy(buffer, (int) (start - position), chunk, (int) (start - offset), (int) (end - start));
            position += count;
        }
        if (position != size) throw new MSException("BUG_EXPORT_FILE_CHANGED");
        return Map.of("offset", offset, "nextOffset", offset + chunk.length, "eof", offset + chunk.length == size,
                "sizeBytes", size, "sha256", HexFormat.of().formatHex(digest.digest()),
                "base64", Base64.getEncoder().encodeToString(chunk));
    }
}
