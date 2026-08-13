package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetDocumentDTO;
import io.metersphere.agent.dto.TestAssetRelationDTO;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.dto.TestAssetContextDocumentDTO;
import io.metersphere.agent.mapper.TestAssetMapper;
import io.metersphere.system.utils.Pager;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TestAssetCatalogService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private TestAssetMapper mapper;
    @Resource
    private AgentProjectService agentProjectService;

    public Pager<List<TestAssetDocumentDTO>> documents(String projectId, String parseStatus, String keyword,
                                                        Integer current, Integer pageSize) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        int page = normalizePage(current);
        int size = normalizePageSize(pageSize);
        String status = StringUtils.equalsIgnoreCase(parseStatus, "ALL") ? null : StringUtils.trimToNull(parseStatus);
        String query = StringUtils.trimToNull(keyword);
        long total = mapper.countDocuments(resolvedProjectId, status, query);
        List<TestAssetDocumentDTO> list = total == 0 ? List.of()
                : mapper.selectDocuments(resolvedProjectId, status, query, (long) (page - 1) * size, size);
        return new Pager<>(list, total, size, page);
    }

    public Pager<List<TestAssetVersionDTO>> versions(String projectId, String assetType, String assetId, String keyword,
                                                      Integer current, Integer pageSize) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        int page = normalizePage(current);
        int size = normalizePageSize(pageSize);
        String type = normalizeType(assetType);
        String id = StringUtils.trimToNull(assetId);
        String query = StringUtils.trimToNull(keyword);
        long total = mapper.countVersions(resolvedProjectId, type, id, query);
        List<TestAssetVersionDTO> list = total == 0 ? List.of()
                : mapper.selectVersions(resolvedProjectId, type, id, query, (long) (page - 1) * size, size);
        return new Pager<>(list, total, size, page);
    }

    public Pager<List<TestAssetRelationDTO>> relations(String projectId, String assetType, String assetId,
                                                        String relationType, String keyword,
                                                        Integer current, Integer pageSize) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        int page = normalizePage(current);
        int size = normalizePageSize(pageSize);
        String type = normalizeType(assetType);
        String id = StringUtils.trimToNull(assetId);
        String relation = StringUtils.upperCase(StringUtils.trimToNull(relationType));
        String query = StringUtils.trimToNull(keyword);
        long total = mapper.countRelations(resolvedProjectId, type, id, relation, query);
        List<TestAssetRelationDTO> list = total == 0 ? List.of()
                : mapper.selectRelations(resolvedProjectId, type, id, relation, query, (long) (page - 1) * size, size);
        return new Pager<>(list, total, size, page);
    }

    public List<TestAssetContextDocumentDTO> documentContextForCases(String projectId, List<String> caseAssetIds) {
        if (caseAssetIds == null || caseAssetIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectDocumentContextForCases(projectId, caseAssetIds.stream().distinct().toList());
    }

    private int normalizePage(Integer current) {
        return current == null || current < 1 ? 1 : current;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    private String normalizeType(String assetType) {
        return StringUtils.upperCase(StringUtils.trimToNull(assetType));
    }
}
