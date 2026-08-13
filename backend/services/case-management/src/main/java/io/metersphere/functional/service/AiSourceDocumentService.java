package io.metersphere.functional.service;

import io.metersphere.functional.constants.AiSourceDocumentParseStatus;
import io.metersphere.functional.domain.AiSourceDocument;
import io.metersphere.functional.dto.AiSourceDocumentDTO;
import io.metersphere.functional.event.TestAssetDocumentPublishedEvent;
import io.metersphere.functional.mapper.AiSourceDocumentMapper;
import io.metersphere.functional.request.AiSourceDocumentIdRequest;
import io.metersphere.functional.request.AiSourceDocumentPageRequest;
import io.metersphere.functional.request.AiSourceDocumentUploadRequest;
import io.metersphere.functional.response.AiSourceDocumentPageResponse;
import io.metersphere.project.service.FileMetadataService;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.service.ai.AiGovernanceService;
import io.metersphere.system.service.ai.AiAuditService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.tika.Tika;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class AiSourceDocumentService {
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "md", "html", "htm", "json", "xml", "yaml", "yml",
            "png", "jpg", "jpeg", "bmp", "gif", "webp");
    private static final Set<String> DENIED_EXTENSIONS = Set.of(
            "exe", "dll", "bat", "cmd", "sh", "ps1", "jar", "msi", "com", "scr");

    @Resource
    private AiSourceDocumentMapper aiSourceDocumentMapper;
    @Resource
    private FileMetadataService fileMetadataService;
    @Resource
    private AiSourceDocumentParserService parserService;
    @Resource
    private AiGovernanceService aiGovernanceService;
    @Resource
    private AiAuditService aiAuditService;
    @Resource
    private AiDocumentVirusScanner virusScanner;
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    public AiSourceDocumentDTO upload(AiSourceDocumentUploadRequest request, MultipartFile file, String userId) {
        validateFile(file);
        byte[] fileBytes = readBytes(file);
        virusScanner.scan(file.getOriginalFilename(), fileBytes);
        String detectedMimeType = validateDetectedType(file.getOriginalFilename(), file.getContentType(), fileBytes);
        return aiGovernanceService.admitFileUpload(request.getProjectId(), request.getConversationId(), userId,
                file.getSize(), () -> persistUpload(request, file, userId, fileBytes, detectedMimeType));
    }

    private AiSourceDocumentDTO persistUpload(AiSourceDocumentUploadRequest request, MultipartFile file, String userId,
                                              byte[] fileBytes, String detectedMimeType) {
        String sha256 = DigestUtils.sha256Hex(fileBytes);
        String originalName = StringUtils.defaultString(file.getOriginalFilename());
        long now = System.currentTimeMillis();
        String documentId = IDGenerator.nextStr();
        String fileId = fileMetadataService.transferFile(
                "ai-doc-" + documentId,
                originalName,
                request.getProjectId(),
                ModuleConstants.DEFAULT_NODE_ID,
                userId,
                fileBytes);

        AiSourceDocument document = new AiSourceDocument();
        document.setId(documentId);
        document.setProjectId(request.getProjectId());
        document.setConversationId(request.getConversationId());
        document.setFileId(fileId);
        document.setOriginalName(originalName);
        document.setMimeType(detectedMimeType);
        document.setFileSize(file.getSize());
        document.setSha256(sha256);
        document.setParseStatus(AiSourceDocumentParseStatus.UPLOADED.name());
        document.setDuplicate(false);
        document.setDeleted(false);
        document.setCreateUser(userId);
        document.setCreateTime(now);
        document.setUpdateTime(now);

        AiSourceDocument reusable = aiSourceDocumentMapper.selectReusableBySha256(request.getProjectId(), userId, sha256);
        if (reusable != null) {
            document.setDuplicate(true);
            document.setDuplicateSourceDocumentId(reusable.getId());
            document.setParseStatus(reusable.getParseStatus());
            document.setParsedResultPath(reusable.getParsedResultPath());
            document.setParserType(reusable.getParserType());
            document.setSummary(reusable.getSummary());
            document.setSectionIndex(reusable.getSectionIndex());
            document.setErrorMessage("识别为重复文件，已复用历史解析结果");
        }
        aiSourceDocumentMapper.insert(document);
        if (Boolean.TRUE.equals(document.getDuplicate())
                && AiSourceDocumentParseStatus.PARSED.name().equals(document.getParseStatus())) {
            applicationEventPublisher.publishEvent(new TestAssetDocumentPublishedEvent(document,
                    io.metersphere.sdk.util.JSON.toJSONString(document)));
        }
        audit("UPLOAD", request.getProjectId(), userId, "documentId=" + documentId + ",fileId=" + fileId + ",duplicate=" + document.getDuplicate());
        if (!Boolean.TRUE.equals(document.getDuplicate())) {
            parserService.parseAsync(documentId);
        }
        return toDTO(document);
    }

    public AiSourceDocumentPageResponse page(AiSourceDocumentPageRequest request, String userId) {
        int current = Math.max(1, request.getCurrent() == null ? 1 : request.getCurrent());
        int pageSize = Math.min(100, Math.max(1, request.getPageSize() == null ? 20 : request.getPageSize()));
        String status = StringUtils.equalsIgnoreCase(request.getParseStatus(), "ALL") ? null : request.getParseStatus();
        AiSourceDocumentPageResponse response = new AiSourceDocumentPageResponse();
        response.setTotal(aiSourceDocumentMapper.countByProjectAndCreateUser(request.getProjectId(), userId, status));
        response.setRecords(aiSourceDocumentMapper.selectByProjectAndCreateUser(
                request.getProjectId(), userId, status, (long) (current - 1) * pageSize, pageSize)
                .stream().map(this::toDTO).toList());
        return response;
    }

    public AiSourceDocumentDTO get(String id, String projectId, String userId) {
        return toDTO(requireDocument(id, projectId, userId));
    }

    public void retry(AiSourceDocumentIdRequest request, String userId) {
        requireDocument(request.getId(), request.getProjectId(), userId);
        parserService.parseAsync(request.getId());
        audit("RETRY_PARSE", request.getProjectId(), userId, "documentId=" + request.getId());
    }

    public void delete(AiSourceDocumentIdRequest request, String userId) {
        requireDocument(request.getId(), request.getProjectId(), userId);
        aiSourceDocumentMapper.markDeleted(request.getId(), request.getProjectId(), userId, System.currentTimeMillis());
        audit("DELETE", request.getProjectId(), userId, "documentId=" + request.getId());
    }

    public ResponseEntity<byte[]> download(AiSourceDocumentIdRequest request, String userId) {
        AiSourceDocument document = requireDocument(request.getId(), request.getProjectId(), userId);
        audit("DOWNLOAD", request.getProjectId(), userId, "documentId=" + request.getId());
        return fileMetadataService.downloadById(document.getFileId());
    }

    private AiSourceDocument requireDocument(String id, String projectId, String userId) {
        AiSourceDocument document = aiSourceDocumentMapper.selectByPrimaryKey(id);
        if (document == null
                || Boolean.TRUE.equals(document.getDeleted())
                || !StringUtils.equals(projectId, document.getProjectId())
                || !StringUtils.equals(userId, document.getCreateUser())) {
            throw new MSException("来源文档不存在或无权限访问");
        }
        return document;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MSException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new MSException("单文件不能超过 50MB");
        }
        String ext = extension(file.getOriginalFilename());
        if (DENIED_EXTENSIONS.contains(ext)) {
            throw new MSException("禁止上传可执行文件用于 AI 自动解析");
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new MSException("不支持的产品方案文件类型：" + ext);
        }
        String mimeType = StringUtils.defaultString(file.getContentType()).toLowerCase(Locale.ROOT);
        if (StringUtils.containsAny(mimeType, "application/x-msdownload", "application/x-sh", "application/x-msdos-program")) {
            throw new MSException("文件 MIME 类型不允许参与自动解析：" + mimeType);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new MSException("读取上传文件失败", ex);
        }
    }

    private String extension(String fileName) {
        int index = StringUtils.lastIndexOf(fileName, ".");
        return index < 0 ? StringUtils.EMPTY : StringUtils.substring(fileName, index + 1).toLowerCase(Locale.ROOT);
    }

    private AiSourceDocumentDTO toDTO(AiSourceDocument document) {
        AiSourceDocumentDTO dto = new AiSourceDocumentDTO();
        dto.setId(document.getId());
        dto.setProjectId(document.getProjectId());
        dto.setConversationId(document.getConversationId());
        dto.setFileId(document.getFileId());
        dto.setOriginalName(document.getOriginalName());
        dto.setMimeType(document.getMimeType());
        dto.setFileSize(document.getFileSize());
        dto.setSha256(document.getSha256());
        dto.setDuplicate(document.getDuplicate());
        dto.setDuplicateSourceDocumentId(document.getDuplicateSourceDocumentId());
        dto.setParseStatus(document.getParseStatus());
        dto.setParsedResultPath(document.getParsedResultPath());
        dto.setParserType(document.getParserType());
        dto.setSummary(document.getSummary());
        dto.setSectionIndex(document.getSectionIndex());
        dto.setErrorMessage(document.getErrorMessage());
        dto.setCreateUser(document.getCreateUser());
        dto.setCreateTime(document.getCreateTime());
        dto.setUpdateTime(document.getUpdateTime());
        dto.setDeleted(document.getDeleted());
        return dto;
    }

    private void audit(String action, String projectId, String userId, String message) {
        log.info("functional_case_ai_document action={}, projectId={}, userId={}, {}", action, projectId, userId, message);
        aiAuditService.record(projectId, null, userId, projectId, action.startsWith("DELETE") ? "DELETE" : "UPDATE",
                "AI_SOURCE_DOCUMENT_" + action, "/functional/case/ai/document", "POST",
                java.util.Map.of("detail", StringUtils.left(StringUtils.defaultString(message), 1000)));
    }

    private String validateDetectedType(String fileName, String claimedMimeType, byte[] content) {
        String detected;
        try {
            detected = new Tika().detect(content, fileName);
        } catch (Exception ex) {
            throw new MSException("无法识别文件真实类型", ex);
        }
        String ext = extension(fileName);
        String actual = StringUtils.lowerCase(StringUtils.defaultString(detected));
        boolean valid = switch (ext) {
            case "pdf" -> actual.contains("pdf");
            case "doc", "xls", "ppt" -> actual.contains("msword") || actual.contains("msoffice")
                    || actual.contains("vnd.ms-") || actual.contains("ole-storage");
            case "docx", "xlsx", "pptx" -> actual.contains("officedocument") || actual.contains("ooxml");
            case "png", "jpg", "jpeg", "bmp", "gif", "webp" -> actual.startsWith("image/");
            default -> actual.startsWith("text/") || actual.contains("json") || actual.contains("xml")
                    || actual.contains("yaml") || "application/octet-stream".equals(actual);
        };
        if (!valid) {
            throw new MSException("文件扩展名与真实 MIME 不匹配：" + ext + " / " + actual);
        }
        if (StringUtils.isNotBlank(claimedMimeType)
                && StringUtils.containsAny(StringUtils.lowerCase(claimedMimeType), "x-msdownload", "x-sh", "x-msdos")) {
            throw new MSException("声明的文件 MIME 类型不安全");
        }
        return actual;
    }
}
