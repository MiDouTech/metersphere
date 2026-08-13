package io.metersphere.functional.service;

import io.metersphere.functional.constants.AiSourceDocumentParseStatus;
import io.metersphere.functional.domain.AiSourceDocument;
import io.metersphere.functional.event.TestAssetDocumentPublishedEvent;
import io.metersphere.functional.mapper.AiSourceDocumentMapper;
import io.metersphere.project.domain.FileMetadata;
import io.metersphere.project.service.FileMetadataService;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.service.ai.AiAuditService;
import jakarta.annotation.Resource;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.xml.sax.SAXException;

@Slf4j
@Service
public class AiSourceDocumentParserService {
    private static final int CHUNK_SIZE = 1800;
    private static final int CHUNK_OVERLAP = 180;
    private static final int MAX_EXTRACTED_CHARACTERS = 2_000_000;
    private static final long PARSE_TIMEOUT_SECONDS = 120;
    private static final Pattern HEADING = Pattern.compile("^(#{1,6}\\s+.+|第[一二三四五六七八九十百0-9]+[章节部分].*|[0-9]+(?:\\.[0-9]+){0,3}\\s+.+)$");
    private final ExecutorService parserExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "ai-document-parser");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private AiSourceDocumentMapper aiSourceDocumentMapper;
    @Resource
    private FileMetadataService fileMetadataService;
    @Resource
    private AiSourceDocumentEventService eventService;
    @Resource
    private AiAuditService aiAuditService;
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @PreDestroy
    void shutdownParserExecutor() {
        parserExecutor.shutdownNow();
    }

    @Async("threadPoolTaskExecutor")
    public void parseAsync(String documentId) {
        parse(documentId);
    }

    public void parse(String documentId) {
        AiSourceDocument document = aiSourceDocumentMapper.selectByPrimaryKey(documentId);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            return;
        }
        updateStatus(documentId, AiSourceDocumentParseStatus.PARSING.name(), null);
        eventService.publish(document.getProjectId(), document.getCreateUser(), documentId,
                AiSourceDocumentParseStatus.PARSING.name(), null);
        try {
            Future<ParseResult> parseFuture = CompletableFuture.supplyAsync(() -> parseFile(document), parserExecutor);
            ParseResult result;
            try {
                result = parseFuture.get(PARSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException timeout) {
                parseFuture.cancel(true);
                throw timeout;
            }
            String resultFileId = fileMetadataService.transferFile(
                    "ai-doc-parsed-" + documentId + "-" + System.currentTimeMillis(),
                    document.getOriginalName() + ".parsed.json",
                    document.getProjectId(),
                    ModuleConstants.DEFAULT_NODE_ID,
                    document.getCreateUser(),
                    JSON.toFormatJSONString(result).getBytes(StandardCharsets.UTF_8));
            AiSourceDocument update = new AiSourceDocument();
            update.setId(documentId);
            update.setParseStatus(AiSourceDocumentParseStatus.PARSED.name());
            update.setParserType(result.getParserType());
            update.setParsedResultPath(resultFileId);
            update.setSummary(result.getSummary());
            update.setSectionIndex(JSON.toJSONString(result.getSections()));
            update.setErrorMessage(StringUtils.EMPTY);
            update.setUpdateTime(System.currentTimeMillis());
            aiSourceDocumentMapper.updateByPrimaryKeySelective(update);
            AiSourceDocument published = aiSourceDocumentMapper.selectByPrimaryKey(documentId);
            applicationEventPublisher.publishEvent(new TestAssetDocumentPublishedEvent(
                    published, JSON.toJSONString(java.util.Map.of(
                    "document", published,
                    "parserType", result.getParserType(),
                    "summary", StringUtils.defaultString(result.getSummary()),
                    "sections", result.getSections()))));
            eventService.publish(document.getProjectId(), document.getCreateUser(), documentId,
                    AiSourceDocumentParseStatus.PARSED.name(), null);
            audit("PARSE", document.getProjectId(), document.getCreateUser(), "documentId=" + documentId + ",chunks=" + result.getSections().size());
        } catch (Exception ex) {
            Throwable actual = ex.getCause() == null ? ex : ex.getCause();
            String error = ex instanceof TimeoutException
                    ? "文档解析超过 " + PARSE_TIMEOUT_SECONDS + " 秒，已终止等待"
                    : StringUtils.defaultIfBlank(actual.getMessage(), actual.getClass().getSimpleName());
            AiSourceDocument update = new AiSourceDocument();
            update.setId(documentId);
            update.setParseStatus(AiSourceDocumentParseStatus.FAILED.name());
            update.setParserType(detectParserType(document.getOriginalName()));
            update.setErrorMessage(StringUtils.left(error, 4000));
            update.setUpdateTime(System.currentTimeMillis());
            aiSourceDocumentMapper.updateByPrimaryKeySelective(update);
            eventService.publish(document.getProjectId(), document.getCreateUser(), documentId,
                    AiSourceDocumentParseStatus.FAILED.name(), error);
            audit("PARSE_FAILED", document.getProjectId(), document.getCreateUser(), "documentId=" + documentId + ",error=" + error);
        }
    }

    private ParseResult parseFile(AiSourceDocument document) {
        String ext = extension(document.getOriginalName());
        FileMetadata fileMetadata = fileMetadataService.selectById(document.getFileId());
        byte[] bytes = fileMetadataService.getFileByte(fileMetadata);
        String text;
        String parserType;
        if (isTextExtractable(ext, document.getMimeType())) {
            text = stripHtmlIfNeeded(ext, new String(bytes, StandardCharsets.UTF_8));
            parserType = "BASIC_TEXT_PARSER";
        } else {
            text = extractWithTika(bytes, document);
            parserType = detectParserType(document.getOriginalName());
        }
        text = normalizeExtractedText(text);
        if (StringUtils.isBlank(text)) {
            throw new IllegalStateException("文件未提取到有效文本");
        }
        ParseResult result = new ParseResult();
        result.setParserType(parserType);
        result.setText(StringUtils.left(text, MAX_EXTRACTED_CHARACTERS));
        result.setSummary(StringUtils.left(text, 1000));
        result.setSections(splitSections(text));
        return result;
    }

    private String normalizeExtractedText(String text) {
        String normalized = StringUtils.defaultString(text).replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll("[\\t\\x0B\\f ]+", " ");
        normalized = normalized.replaceAll(" *\\n *", "\n");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return StringUtils.trim(normalized);
    }

    String extractWithTika(byte[] bytes, AiSourceDocument document) {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(MAX_EXTRACTED_CHARACTERS);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, document.getOriginalName());
        if (StringUtils.isNotBlank(document.getMimeType())) {
            metadata.set(Metadata.CONTENT_TYPE, document.getMimeType());
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            parser.parse(input, handler, metadata, new ParseContext());
            String result = handler.toString();
            if (isImage(document.getOriginalName()) && StringUtils.isBlank(result)) {
                throw new IllegalStateException("图片 OCR 未提取到文本，请确认部署环境已安装并配置 Tesseract");
            }
            return result;
        } catch (TikaException | SAXException | java.io.IOException ex) {
            throw new IllegalStateException("文档解析失败：" + ex.getMessage(), ex);
        }
    }

    private String stripHtmlIfNeeded(String ext, String text) {
        if (!StringUtils.equalsAnyIgnoreCase(ext, "html", "htm")) {
            return text;
        }
        return text.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ");
    }

    List<Section> splitSections(String text) {
        List<Section> sections = new ArrayList<>();
        int index = 0;
        int sectionNum = 1;
        while (index < text.length()) {
            int end = Math.min(text.length(), index + CHUNK_SIZE);
            if (end < text.length()) {
                int boundary = Math.max(text.lastIndexOf('\n', end), text.lastIndexOf('。', end));
                if (boundary > index + CHUNK_SIZE / 2) {
                    end = boundary + 1;
                }
            }
            Section section = new Section();
            section.setIndex(sectionNum);
            section.setTitle(findSectionTitle(text, index, sectionNum));
            section.setStart(index);
            section.setEnd(end);
            section.setText(text.substring(index, end));
            sections.add(section);
            index = end >= text.length() ? end : Math.max(index + 1, end - CHUNK_OVERLAP);
            sectionNum++;
        }
        return sections;
    }

    private String findSectionTitle(String text, int start, int sectionNum) {
        int searchStart = Math.max(0, start - 800);
        String[] lines = text.substring(searchStart, Math.min(text.length(), start + 200)).split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = StringUtils.trim(lines[i]);
            if (HEADING.matcher(line).matches()) {
                return StringUtils.left(line, 200);
            }
        }
        return "chunk-" + sectionNum;
    }

    private boolean isImage(String fileName) {
        return StringUtils.equalsAnyIgnoreCase(extension(fileName), "png", "jpg", "jpeg", "bmp", "gif", "webp");
    }

    private boolean isTextExtractable(String ext, String mimeType) {
        return StringUtils.equalsAnyIgnoreCase(ext, "txt", "md", "json", "xml", "yaml", "yml", "html", "htm")
                || StringUtils.startsWithIgnoreCase(mimeType, "text/");
    }

    private String detectParserType(String fileName) {
        String ext = extension(fileName);
        if (StringUtils.equalsAnyIgnoreCase(ext, "png", "jpg", "jpeg", "bmp", "gif", "webp")) {
            return "TIKA_TESSERACT_OCR";
        }
        if (StringUtils.equalsAnyIgnoreCase(ext, "pdf")) {
            return "TIKA_PDF";
        }
        if (StringUtils.equalsAnyIgnoreCase(ext, "doc", "docx", "xls", "xlsx", "ppt", "pptx")) {
            return "TIKA_OFFICE";
        }
        return "BASIC_TEXT_PARSER";
    }

    private String extension(String fileName) {
        int index = StringUtils.lastIndexOf(fileName, ".");
        return index < 0 ? StringUtils.EMPTY : StringUtils.substring(fileName, index + 1).toLowerCase(Locale.ROOT);
    }

    private void updateStatus(String documentId, String status, String message) {
        AiSourceDocument update = new AiSourceDocument();
        update.setId(documentId);
        update.setParseStatus(status);
        update.setErrorMessage(message);
        update.setUpdateTime(System.currentTimeMillis());
        aiSourceDocumentMapper.updateByPrimaryKeySelective(update);
    }

    private void audit(String action, String projectId, String userId, String message) {
        log.info("functional_case_ai_document action={}, projectId={}, userId={}, {}", action, projectId, userId, message);
        aiAuditService.record(projectId, null, userId, projectId,
                action.endsWith("FAILED") ? "ERROR" : "UPDATE", "AI_SOURCE_DOCUMENT_" + action,
                "/functional/case/ai/document/parse", "ASYNC",
                java.util.Map.of("detail", StringUtils.left(StringUtils.defaultString(message), 1000)));
    }

    @Data
    public static class ParseResult {
        private String parserType;
        private String summary;
        private String text;
        private List<Section> sections = new ArrayList<>();
    }

    @Data
    public static class Section {
        private int index;
        private String title;
        private int start;
        private int end;
        private String text;
    }
}
