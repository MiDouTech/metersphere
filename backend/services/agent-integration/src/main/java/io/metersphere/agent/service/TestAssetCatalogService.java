package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetDocumentDTO;
import io.metersphere.agent.dto.TestAssetRelationDTO;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.dto.TestAssetContextDocumentDTO;
import io.metersphere.agent.dto.TestAssetCatalogItemDTO;
import io.metersphere.agent.dto.TestAssetContextDTO;
import io.metersphere.agent.dto.TestAssetRefDTO;
import io.metersphere.agent.dto.TestAssetExecutableSnapshotDTO;
import io.metersphere.agent.mapper.TestAssetMapper;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.CompressUtils;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.system.utils.Pager;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class TestAssetCatalogService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ASSET_SNAPSHOT_CHARS = 2_000_000;
    private static final Pattern SENSITIVE_TEXT = Pattern.compile(
            "(?i)((?:password|passwd|token|secret|cookie|authorization|api[-_]?key|private[-_]?key)\\s*[:=]\\s*)([^,;\\s]+)");

    @Resource
    private TestAssetMapper mapper;
    @Resource
    private AgentProjectService agentProjectService;
    @Resource
    private TestAssetVersionService versionService;

    private static final Map<String, List<String>> ASSET_PERMISSIONS = Map.ofEntries(
            Map.entry("DOCUMENT", List.of(PermissionConstants.FUNCTIONAL_CASE_AI_READ)),
            Map.entry("CASE", List.of(PermissionConstants.FUNCTIONAL_CASE_READ)),
            Map.entry("PLAN", List.of(PermissionConstants.TEST_PLAN_READ)),
            Map.entry("TASK", List.of(PermissionConstants.AI_EXECUTION_READ)),
            Map.entry("STEP", List.of(PermissionConstants.AI_EXECUTION_READ)),
            Map.entry("DATASET", List.of(PermissionConstants.PROJECT_FILE_MANAGEMENT_READ)),
            Map.entry("ENVIRONMENT", List.of(PermissionConstants.PROJECT_ENVIRONMENT_READ)),
            Map.entry("COMMON_STEP", List.of(PermissionConstants.PROJECT_API_SCENARIO_READ)),
            Map.entry("API_DEFINITION", List.of(PermissionConstants.PROJECT_API_DEFINITION_READ)),
            Map.entry("EVIDENCE", List.of(PermissionConstants.AI_EXECUTION_READ)),
            Map.entry("BUG", List.of(PermissionConstants.PROJECT_BUG_READ))
            ,Map.entry("BUSINESS_FLOW", List.of(PermissionConstants.AI_EXECUTION_READ))
            ,Map.entry("PAGE_OBJECT", List.of(PermissionConstants.AI_EXECUTION_READ))
    );
    private static final Set<String> CATALOG_TYPES = Set.of(
            "CASE", "DOCUMENT", "PLAN", "DATASET", "ENVIRONMENT", "PAGE_OBJECT", "BUSINESS_FLOW",
            "COMMON_STEP", "API_DEFINITION", "EVIDENCE", "BUG");

    public Pager<List<TestAssetCatalogItemDTO>> catalog(String projectId, String assetType, String keyword,
                                                         String status, Integer current, Integer pageSize) {
        return catalog(projectId, assetType, keyword, status, null, current, pageSize);
    }

    public Pager<List<TestAssetCatalogItemDTO>> catalog(String projectId, String assetType, String keyword,
                                                         String status, Long updatedAfter, Integer current, Integer pageSize) {
        return catalog(projectId, assetType, keyword, status, updatedAfter, null, null, false, current, pageSize);
    }

    public Pager<List<TestAssetCatalogItemDTO>> catalog(String projectId, String assetType, String keyword,
                                                         String status, Long updatedAfter, List<String> creationSources,
                                                         String categoryId, boolean includeDescendants,
                                                         Integer current, Integer pageSize) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        String type = requireCatalogType(assetType);
        assertPermission(type);
        int page = normalizePage(current);
        int size = normalizePageSize(pageSize);
        String query = StringUtils.trimToNull(keyword);
        String normalizedStatus = StringUtils.upperCase(StringUtils.trimToNull(status));
        List<String> sources = creationSources == null ? List.of() : creationSources.stream()
                .map(value -> StringUtils.upperCase(StringUtils.trim(value))).filter(TestAssetGovernanceService.SOURCES::contains)
                .distinct().toList();
        String category = StringUtils.trimToNull(categoryId);
        long total = mapper.countCatalog(resolvedProjectId, type, query, normalizedStatus, updatedAfter,
                sources, category, includeDescendants);
        List<TestAssetCatalogItemDTO> list = total == 0 ? List.of()
                : mapper.selectCatalog(resolvedProjectId, type, query, normalizedStatus, updatedAfter,
                sources, category, includeDescendants, (long) (page - 1) * size, size);
        list.forEach(this::attachLatestPublishedVersion);
        return new Pager<>(list, total, size, page);
    }

    public TestAssetCatalogItemDTO detail(String projectId, String assetType, String assetId) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        String type = requireCatalogType(assetType);
        assertPermission(type);
        TestAssetCatalogItemDTO item = mapper.selectCatalogItem(resolvedProjectId, type, StringUtils.trim(assetId));
        if (item == null) {
            throw new MSException("测试资产不存在或不属于当前项目：" + type + "/" + assetId);
        }
        attachLatestPublishedVersion(item);
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public TestAssetCatalogItemDTO publishAsset(String projectId,String assetType,String assetId){
        String resolvedProjectId=agentProjectService.resolveProjectId(projectId);String type=requireCatalogType(assetType);assertPermission(type);
        TestAssetCatalogItemDTO item=mapper.selectCatalogItem(resolvedProjectId,type,StringUtils.trim(assetId));
        if(item==null)throw new MSException("ASSET_NOT_FOUND");
        TestAssetVersionDTO version=publish(item,StringUtils.defaultIfBlank(SessionUtils.getUserId(),AgentExecutionActorContext.get()));
        if(StringUtils.isBlank(version.getPublishedBy()))throw new MSException("ASSET_PUBLICATION_ACTOR_REQUIRED");
        attachLatestPublishedVersion(item);return item;
    }

    @Transactional(rollbackFor = Exception.class)
    public TestAssetVersionDTO deprecateVersion(String projectId,String versionId,String assetType,String assetId){
        String resolvedProjectId=agentProjectService.resolveProjectId(projectId);String type=requireCatalogType(assetType);assertPermission(type);
        return versionService.deprecate(StringUtils.trim(versionId),resolvedProjectId,type,StringUtils.trim(assetId));
    }

    public List<TestAssetContextDTO> resolveContext(String projectId, List<TestAssetRefDTO> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        if (refs.size() > 50) {
            throw new MSException("单个任务最多引用 50 个扩展测试资产");
        }
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        Set<String> seen = new java.util.HashSet<>();
        return refs.stream().map(ref -> {
            String type = requireCatalogType(ref.getAssetType());
            assertPermission(type);
            String assetId = StringUtils.trimToNull(ref.getAssetId());
            if (assetId == null || !seen.add(type + ":" + assetId)) {
                throw new MSException("测试资产引用为空或重复：" + type + "/" + ref.getAssetId());
            }
            TestAssetCatalogItemDTO item = mapper.selectCatalogItem(resolvedProjectId, type, assetId);
            if (item == null) {
                throw new MSException("测试资产不存在或不属于当前项目：" + type + "/" + assetId);
            }
            TestAssetVersionDTO version;
            if (StringUtils.isNotBlank(ref.getVersionId())) {
                version = versionService.getPublished(ref.getVersionId(), resolvedProjectId, type, assetId);
            } else {
                version = versionService.latestPublished(resolvedProjectId, type, assetId);
            }
            TestAssetContextDTO context = new TestAssetContextDTO();
            context.setAssetType(type);
            context.setAssetId(assetId);
            context.setAssetName(item.getName());
            context.setVersionId(version.getId());
            context.setVersionNo(version.getVersionNo());
            context.setContentHash(version.getContentHash());
            context.setContentSnapshot(version.getContentSnapshot());
            return context;
        }).toList();
    }

    public Pager<List<TestAssetDocumentDTO>> documents(String projectId, String parseStatus, String keyword,
                                                        Integer current, Integer pageSize) {
        return documents(projectId, parseStatus, keyword, null, null, false, current, pageSize);
    }

    public Pager<List<TestAssetDocumentDTO>> documents(String projectId, String parseStatus, String keyword,
                                                        List<String> creationSources, String categoryId, boolean includeDescendants,
                                                        Integer current, Integer pageSize) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        int page = normalizePage(current);
        int size = normalizePageSize(pageSize);
        String status = StringUtils.equalsIgnoreCase(parseStatus, "ALL") ? null : StringUtils.trimToNull(parseStatus);
        String query = StringUtils.trimToNull(keyword);
        List<String> sources = normalizeSources(creationSources); String category = StringUtils.trimToNull(categoryId);
        long total = mapper.countDocuments(resolvedProjectId, status, query, sources, category, includeDescendants);
        List<TestAssetDocumentDTO> list = total == 0 ? List.of()
                : mapper.selectDocuments(resolvedProjectId, status, query, sources, category, includeDescendants,
                (long) (page - 1) * size, size);
        return new Pager<>(list, total, size, page);
    }

    public Pager<List<TestAssetVersionDTO>> versions(String projectId, String assetType, String assetId, String keyword,
                                                      Integer current, Integer pageSize) {
        return versions(projectId, assetType, assetId, keyword, null, null, false, current, pageSize);
    }

    public Pager<List<TestAssetVersionDTO>> versions(String projectId, String assetType, String assetId, String keyword,
                                                      List<String> creationSources, String categoryId, boolean includeDescendants,
                                                      Integer current, Integer pageSize) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        int page = normalizePage(current);
        int size = normalizePageSize(pageSize);
        String type = normalizeType(assetType);
        List<String> allowedTypes = resolveAllowedTypes(type);
        String id = StringUtils.trimToNull(assetId);
        String query = StringUtils.trimToNull(keyword);
        List<String> sources = normalizeSources(creationSources); String category = StringUtils.trimToNull(categoryId);
        long total = mapper.countVersions(resolvedProjectId, allowedTypes, type, id, query, sources, category, includeDescendants);
        List<TestAssetVersionDTO> list = total == 0 ? List.of()
                : mapper.selectVersions(resolvedProjectId, allowedTypes, type, id, query, sources, category, includeDescendants,
                (long) (page - 1) * size, size);
        return new Pager<>(list, total, size, page);
    }

    public Pager<List<TestAssetCatalogItemDTO>> searchByTypes(String projectId, List<String> assetTypes,
                                                               String keyword, String status,
                                                               Integer current, Integer pageSize) {
        return searchByTypes(projectId, assetTypes, keyword, status, null, current, pageSize);
    }

    public Pager<List<TestAssetCatalogItemDTO>> searchByTypes(String projectId, List<String> assetTypes,
                                                               String keyword, String status, Long updatedAfter,
                                                               Integer current, Integer pageSize) {
        if (assetTypes == null || assetTypes.isEmpty()) throw new MSException("ASSET_TYPES_REQUIRED");
        List<String> types = assetTypes.stream().map(this::requireCatalogType).distinct().toList();
        types.forEach(this::assertPermission);
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        int page = normalizePage(current);int size = normalizePageSize(pageSize);int fetch = page * size;
        String query = StringUtils.trimToNull(keyword);
        String normalizedStatus = StringUtils.defaultIfBlank(StringUtils.upperCase(StringUtils.trimToNull(status)), "PUBLISHED");
        long total = 0;List<TestAssetCatalogItemDTO> merged = new ArrayList<>();
        for (String type : types) {
            total += mapper.countCatalog(resolvedProjectId, type, query, normalizedStatus, updatedAfter, List.of(), null, false);
            merged.addAll(mapper.selectCatalog(resolvedProjectId, type, query, normalizedStatus, updatedAfter,
                    List.of(), null, false, 0, fetch));
        }
        merged.sort(Comparator.comparing(TestAssetCatalogItemDTO::getUpdateTime,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(TestAssetCatalogItemDTO::getId));
        int from = Math.min((page - 1) * size, merged.size());int to = Math.min(from + size, merged.size());
        List<TestAssetCatalogItemDTO> result = new ArrayList<>(merged.subList(from, to));
        result.forEach(this::attachLatestPublishedVersion);
        return new Pager<>(result, total, size, page);
    }

    public TestAssetVersionDTO version(String projectId,String versionId){String resolved=agentProjectService.resolveProjectId(projectId);TestAssetVersionDTO version=mapper.selectVersionById(versionId);if(version==null||!resolved.equals(version.getProjectId()))throw new MSException("ASSET_VERSION_NOT_FOUND");assertPermission(version.getAssetType());return version;}

    public TestAssetVersionDTO latestPublishedVersion(String projectId,String assetType,String assetId){String resolved=agentProjectService.resolveProjectId(projectId);String type=normalizeType(assetType);assertPermission(type);return versionService.latestPublished(resolved,type,StringUtils.trim(assetId));}

    public Pager<List<TestAssetRelationDTO>> relations(String projectId, String assetType, String assetId,
                                                        String relationType, String keyword,
                                                        Integer current, Integer pageSize) {
        return relations(projectId, assetType, assetId, relationType, keyword, null, null, false, current, pageSize);
    }

    public Pager<List<TestAssetRelationDTO>> relations(String projectId, String assetType, String assetId,
                                                        String relationType, String keyword,
                                                        List<String> creationSources, String categoryId, boolean includeDescendants,
                                                        Integer current, Integer pageSize) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        int page = normalizePage(current);
        int size = normalizePageSize(pageSize);
        String type = normalizeType(assetType);
        List<String> allowedTypes = resolveAllowedTypes(type);
        String id = StringUtils.trimToNull(assetId);
        String relation = StringUtils.upperCase(StringUtils.trimToNull(relationType));
        String query = StringUtils.trimToNull(keyword);
        List<String> sources = normalizeSources(creationSources); String category = StringUtils.trimToNull(categoryId);
        long total = mapper.countRelations(resolvedProjectId, allowedTypes, type, id, relation, query,
                sources, category, includeDescendants);
        List<TestAssetRelationDTO> list = total == 0 ? List.of()
                : mapper.selectRelations(resolvedProjectId, allowedTypes, type, id, relation, query,
                sources, category, includeDescendants,
                (long) (page - 1) * size, size);
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

    private List<String> normalizeSources(List<String> values) {
        return values == null ? List.of() : values.stream().map(value -> StringUtils.upperCase(StringUtils.trim(value)))
                .filter(TestAssetGovernanceService.SOURCES::contains).distinct().toList();
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    private String normalizeType(String assetType) {
        return StringUtils.upperCase(StringUtils.trimToNull(assetType));
    }

    private String requireCatalogType(String assetType) {
        String type = normalizeType(assetType);
        if (!CATALOG_TYPES.contains(type)) {
            throw new MSException("不支持的测试资产类型：" + StringUtils.defaultString(type, "<empty>"));
        }
        return type;
    }

    private void assertPermission(String type) {
        List<String> permissions = ASSET_PERMISSIONS.get(type);
        if (permissions == null || SecurityUtils.getSubject() == null
                || permissions.stream().noneMatch(SecurityUtils.getSubject()::isPermitted)) {
            throw new MSException("缺少测试资产读取权限：" + String.join(" or ",
                    permissions == null ? List.of("<unknown>") : permissions));
        }
    }

    private List<String> resolveAllowedTypes(String requestedType) {
        if (requestedType != null) {
            if (!ASSET_PERMISSIONS.containsKey(requestedType)) {
                throw new MSException("不支持的测试资产类型：" + requestedType);
            }
            assertPermission(requestedType);
            return List.of(requestedType);
        }
        if (SecurityUtils.getSubject() == null) {
            throw new MSException("缺少测试资产读取权限");
        }
        List<String> allowed = ASSET_PERMISSIONS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(SecurityUtils.getSubject()::isPermitted))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (allowed.isEmpty()) {
            throw new MSException("缺少测试资产读取权限");
        }
        return allowed;
    }

    private void attachLatestPublishedVersion(TestAssetCatalogItemDTO item) {
        TestAssetVersionDTO version = mapper.selectLatestPublished(item.getProjectId(), item.getAssetType(), item.getId());
        if (version == null) {
            item.setAssetVersionId(null);
            item.setAssetVersionNo(null);
            item.setContentHash(null);
            return;
        }
        item.setAssetVersionId(version.getId());
        item.setAssetVersionNo(version.getVersionNo());
        item.setContentHash(version.getContentHash());
    }

    private TestAssetVersionDTO publish(TestAssetCatalogItemDTO item, String userId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assetType", item.getAssetType());
        snapshot.put("assetId", item.getId());
        snapshot.put("name", item.getName());
        snapshot.put("category", item.getCategory());
        snapshot.put("status", item.getStatus());
        snapshot.put("summary", item.getSummary());
        snapshot.put("owner", item.getOwner());
        snapshot.put("updateTime", item.getUpdateTime());
        TestAssetExecutableSnapshotDTO executable = mapper.selectExecutableSnapshot(
                item.getProjectId(), item.getAssetType(), item.getId());
        if (executable == null) {
            throw new MSException("测试资产内容不存在或不属于当前项目：" + item.getAssetType() + "/" + item.getId());
        }
        appendExecutableSnapshot(snapshot, item, executable);
        String content = JSON.toJSONString(snapshot);
        if (content.length() > MAX_ASSET_SNAPSHOT_CHARS) {
            throw new MSException("测试资产快照超过 2 MB，请拆分资产后重试：" + item.getName());
        }
        return versionService.publish(item.getProjectId(), item.getAssetType(), item.getId(),
                item.getSourceVersion(), content, userId);
    }

    private void appendExecutableSnapshot(Map<String, Object> snapshot, TestAssetCatalogItemDTO item,
                                          TestAssetExecutableSnapshotDTO source) {
        switch (item.getAssetType()) {
            case "DATASET" -> {
                snapshot.put("fileId", source.getFileId());
                snapshot.put("fileName", source.getFileName());
                snapshot.put("fileType", source.getFileType());
                snapshot.put("fileSize", source.getFileSize());
                snapshot.put("fileVersion", item.getSourceVersion());
                snapshot.put("moduleId", source.getModuleId());
                snapshot.put("tags", parseAndSanitize(source.getTagsJson(), "dataset:" + item.getId()));
                snapshot.put("contentAccess", Map.of(
                        "mode", "FIXED_FILE_REFERENCE",
                        "fileId", StringUtils.defaultString(source.getFileId()),
                        "endpoint", "/project/file/download/" + StringUtils.defaultString(source.getFileId())));
            }
            case "ENVIRONMENT" -> snapshot.put("configuration",
                    parseAndSanitize(decodeBlob(source.getEnvironmentConfig()), "environment:" + item.getId()));
            case "COMMON_STEP" -> {
                snapshot.put("parameters", parseAndSanitize(decodeBlob(source.getCommonParams()), "common-step:" + item.getId()));
                snapshot.put("script", sanitizeText(decodeBlob(source.getCommonScript())));
                snapshot.put("resultSchema", parseAndSanitize(decodeBlob(source.getCommonResult()), "common-step:" + item.getId()));
                snapshot.put("tags", parseAndSanitize(source.getTagsJson(), "common-step:" + item.getId()));
            }
            case "API_DEFINITION" -> {
                snapshot.put("protocol", source.getProtocol());
                snapshot.put("method", source.getHttpMethod());
                snapshot.put("path", source.getApiPath());
                snapshot.put("moduleId", source.getModuleId());
                snapshot.put("tags", parseAndSanitize(source.getTagsJson(), "api-definition:" + item.getId()));
                snapshot.put("request", parseAndSanitize(decodeBlob(source.getApiRequest()), "api-definition:" + item.getId()));
                snapshot.put("response", parseAndSanitize(decodeBlob(source.getApiResponse()), "api-definition:" + item.getId()));
            }
            case "EVIDENCE" -> {
                snapshot.put("taskId", source.getTaskId());
                snapshot.put("executionCaseId", source.getExecutionCaseId());
                snapshot.put("caseId", source.getCaseId());
                snapshot.put("stepId", source.getStepId());
                snapshot.put("purpose", source.getPurpose());
                snapshot.put("contentType", source.getContentType());
                snapshot.put("sizeBytes", source.getSizeBytes());
                snapshot.put("sha256", source.getSha256());
                snapshot.put("redacted", source.getRedacted());
                snapshot.put("retentionUntil", source.getRetentionUntil());
                snapshot.put("contentAccess", Map.of(
                        "mode", "AUTHORIZED_DOWNLOAD",
                        "endpoint", "/ai/execution/task/" + StringUtils.defaultString(source.getTaskId())
                                + "/artifact/" + item.getId()));
            }
            case "BUG" -> {
                snapshot.put("bugNumber", source.getBugNumber());
                snapshot.put("description", sanitizeText(source.getBugDescription()));
                snapshot.put("handleUser", source.getHandleUser());
                snapshot.put("tags", parseAndSanitize(source.getTagsJson(), "bug:" + item.getId()));
            }
            case "CASE", "DOCUMENT", "PLAN", "PAGE_OBJECT", "BUSINESS_FLOW" ->
                    snapshot.put("content", parseAndSanitize(source.getRawContentJson(), item.getAssetType().toLowerCase(Locale.ROOT)+":"+item.getId()));
            default -> throw new MSException("不支持的测试资产类型：" + item.getAssetType());
        }
    }

    private Object parseAndSanitize(String value, String referencePrefix) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return sanitizeNode(JSON.parseObject(value, Object.class), referencePrefix);
        } catch (Exception ignored) {
            return sanitizeText(value);
        }
    }

    private String decodeBlob(byte[] value) {
        if (value == null) {
            return null;
        }
        Object decoded = CompressUtils.unzip(value);
        byte[] bytes = decoded instanceof byte[] result ? result : value;
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Object sanitizeNode(Object value, String referencePrefix) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            source.forEach((key, child) -> {
                String name = String.valueOf(key);
                if (isSensitiveKey(name)) {
                    sanitized.put(name, Map.of("valueRef", referencePrefix + ":" + name));
                } else {
                    sanitized.put(name, sanitizeNode(child, referencePrefix + ":" + name));
                }
            });
            return sanitized;
        }
        if (value instanceof Collection<?> source) {
            return source.stream().map(child -> sanitizeNode(child, referencePrefix)).toList();
        }
        return value instanceof String text ? sanitizeText(text) : value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = StringUtils.lowerCase(StringUtils.defaultString(key)).replace("_", "").replace("-", "");
        return normalized.contains("password") || normalized.contains("passwd") || normalized.contains("token")
                || normalized.contains("secret") || normalized.contains("cookie")
                || normalized.contains("authorization") || normalized.contains("apikey")
                || normalized.contains("privatekey");
    }

    private String sanitizeText(String value) {
        return value == null ? null : SENSITIVE_TEXT.matcher(value).replaceAll("$1***");
    }
}
