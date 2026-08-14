package io.metersphere.functional.service;

import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiCaseDocumentSearchService {
    private static final int ABSOLUTE_MAX_RESULTS = 10;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public String search(String projectId, String conversationId, String userId, List<String> sourceDocumentIds,
                         String query, Integer maxResults) {
        int limit = Math.min(ABSOLUTE_MAX_RESULTS, Math.max(1, maxResults == null ? 5 : maxResults));
        List<String> terms = tokenize(query);
        List<Map<String, Object>> candidates = new ArrayList<>();
        if (sourceDocumentIds == null || sourceDocumentIds.isEmpty()) {
            return emptyResult(query);
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(sourceDocumentIds.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(projectId);
        parameters.addAll(sourceDocumentIds);
        List<Map<String, Object>> documents = jdbcTemplate.queryForList("""
                SELECT id, original_name, summary, section_index
                FROM ai_source_document
                WHERE project_id=? AND deleted=0 AND parse_status='PARSED'
                  AND id IN (%s)
                ORDER BY update_time DESC LIMIT 50
                """.formatted(placeholders), parameters.toArray());
        for (Map<String, Object> document : documents) {
            String sectionIndex = (String) document.get("section_index");
            List<AiSourceDocumentParserService.Section> sections = StringUtils.isBlank(sectionIndex)
                    ? List.of() : JSON.parseArray(sectionIndex, AiSourceDocumentParserService.Section.class);
            if (sections.isEmpty() && StringUtils.isNotBlank((String) document.get("summary"))) {
                AiSourceDocumentParserService.Section summary = new AiSourceDocumentParserService.Section();
                summary.setIndex(0);
                summary.setTitle("摘要");
                summary.setText((String) document.get("summary"));
                sections = List.of(summary);
            }
            for (AiSourceDocumentParserService.Section section : sections) {
                int score = score(section.getTitle() + "\n" + section.getText(), terms);
                if (score <= 0 && !terms.isEmpty()) {
                    continue;
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("documentId", document.get("id"));
                result.put("documentName", document.get("original_name"));
                result.put("sectionIndex", section.getIndex());
                result.put("sectionTitle", StringUtils.defaultIfBlank(section.getTitle(), "正文"));
                result.put("quote", StringUtils.left(StringUtils.defaultString(section.getText()), 800));
                result.put("score", score);
                candidates.add(result);
            }
        }
        candidates.sort(Comparator.comparingInt(item -> -((Number) item.get("score")).intValue()));
        return JSON.toJSONString(Map.of(
                "query", StringUtils.left(query, 500),
                "results", candidates.stream().limit(limit).toList(),
                "resultCount", Math.min(limit, candidates.size())));
    }

    private String emptyResult(String query) {
        return JSON.toJSONString(Map.of(
                "query", StringUtils.left(StringUtils.defaultString(query), 500),
                "results", List.of(),
                "resultCount", 0));
    }

    private List<String> tokenize(String query) {
        if (StringUtils.isBlank(query)) {
            return List.of();
        }
        return List.of(StringUtils.lowerCase(query, Locale.ROOT).split("[\\s,，。；;：:、]+"))
                .stream().filter(term -> term.length() >= 2).distinct().limit(12).toList();
    }

    private int score(String content, List<String> terms) {
        String normalized = StringUtils.lowerCase(StringUtils.defaultString(content), Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            int from = 0;
            while ((from = normalized.indexOf(term, from)) >= 0) {
                score += 1;
                from += term.length();
            }
        }
        return score;
    }
}
