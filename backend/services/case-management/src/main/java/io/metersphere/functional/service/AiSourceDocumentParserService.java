package io.metersphere.functional.service;

import io.metersphere.functional.constants.AiSourceDocumentParseStatus;
import io.metersphere.functional.domain.AiSourceDocument;
import io.metersphere.functional.mapper.AiSourceDocumentMapper;
import io.metersphere.project.domain.FileMetadata;
import io.metersphere.project.service.FileMetadataService;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class AiSourceDocumentParserService {
    private static final int CHUNK_SIZE = 1800;

    @Resource
    private AiSourceDocumentMapper aiSourceDocumentMapper;
    @Resource
    private FileMetadataService fileMetadataService;

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
        try {
            ParseResult result = parseFile(document);
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
            audit("PARSE", document.getProjectId(), document.getCreateUser(), "documentId=" + documentId + ",chunks=" + result.getSections().size());
        } catch (Exception ex) {
            AiSourceDocument update = new AiSourceDocument();
            update.setId(documentId);
            update.setParseStatus(AiSourceDocumentParseStatus.FAILED.name());
            update.setParserType(detectParserType(document.getOriginalName()));
            update.setErrorMessage(StringUtils.left(ex.getMessage(), 4000));
            update.setUpdateTime(System.currentTimeMillis());
            aiSourceDocumentMapper.updateByPrimaryKeySelective(update);
            audit("PARSE_FAILED", document.getProjectId(), document.getCreateUser(), "documentId=" + documentId + ",error=" + ex.getMessage());
        }
    }

    private ParseResult parseFile(AiSourceDocument document) {
        String ext = extension(document.getOriginalName());
        FileMetadata fileMetadata = fileMetadataService.selectById(document.getFileId());
        byte[] bytes = fileMetadataService.getFileByte(fileMetadata);
        if (!isTextExtractable(ext, document.getMimeType())) {
            throw new IllegalStateException("当前文件类型已存档，但未接入自动解析器/OCR：" + ext);
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        text = stripHtmlIfNeeded(ext, text);
        text = StringUtils.normalizeSpace(text);
        if (StringUtils.isBlank(text)) {
            throw new IllegalStateException("文件未提取到有效文本");
        }
        ParseResult result = new ParseResult();
        result.setParserType(detectParserType(document.getOriginalName()));
        result.setText(text);
        result.setSummary(StringUtils.left(text, 1000));
        result.setSections(splitSections(text));
        return result;
    }

    private String stripHtmlIfNeeded(String ext, String text) {
        if (!StringUtils.equalsAnyIgnoreCase(ext, "html", "htm")) {
            return text;
        }
        return text.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ");
    }

    private List<Section> splitSections(String text) {
        List<Section> sections = new ArrayList<>();
        int index = 0;
        int sectionNum = 1;
        while (index < text.length()) {
            int end = Math.min(text.length(), index + CHUNK_SIZE);
            Section section = new Section();
            section.setIndex(sectionNum);
            section.setTitle("chunk-" + sectionNum);
            section.setStart(index);
            section.setEnd(end);
            section.setText(text.substring(index, end));
            sections.add(section);
            index = end;
            sectionNum++;
        }
        return sections;
    }

    private boolean isTextExtractable(String ext, String mimeType) {
        return StringUtils.equalsAnyIgnoreCase(ext, "txt", "md", "json", "xml", "yaml", "yml", "html", "htm")
                || StringUtils.startsWithIgnoreCase(mimeType, "text/");
    }

    private String detectParserType(String fileName) {
        String ext = extension(fileName);
        if (StringUtils.equalsAnyIgnoreCase(ext, "png", "jpg", "jpeg", "bmp", "gif", "webp")) {
            return "IMAGE_OCR_NOT_CONFIGURED";
        }
        if (StringUtils.equalsAnyIgnoreCase(ext, "pdf")) {
            return "PDF_PARSER_NOT_CONFIGURED";
        }
        if (StringUtils.equalsAnyIgnoreCase(ext, "doc", "docx", "xls", "xlsx", "ppt", "pptx")) {
            return "OFFICE_PARSER_NOT_CONFIGURED";
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
