package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.shiro.SecurityUtils;
import io.metersphere.sdk.constants.PermissionConstants;

import jakarta.annotation.Resource;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class TestAssetGovernanceService {
    public static final Set<String> SOURCES = Set.of("MANUAL", "AI", "IMPORT", "SYNC", "AUTOMATION", "UNKNOWN");
    private static final int MAX_LEVEL = 5;

    @Resource private JdbcTemplate jdbc;
    @Resource private TestAssetCatalogService catalogService;

    public List<TestAssetCategoryDTO> tree(String keyword) {
        String organizationId = requireOrganization();
        List<TestAssetCategoryDTO> rows = jdbc.query("""
                SELECT c.id,c.parent_id,c.name,c.path,c.level,c.sort,
                       (SELECT COUNT(1) FROM test_asset_metadata m WHERE m.category_id=c.id) asset_count
                FROM test_asset_category c WHERE c.organization_id=? AND c.deleted=b'0' ORDER BY c.sort,c.name
                """, (rs, n) -> {
            TestAssetCategoryDTO dto = new TestAssetCategoryDTO();
            dto.setId(rs.getString("id")); dto.setParentId(rs.getString("parent_id"));
            dto.setName(rs.getString("name")); dto.setPath(rs.getString("path"));
            dto.setLevel(rs.getInt("level")); dto.setSort(rs.getLong("sort"));
            dto.setAssetCount(rs.getLong("asset_count")); return dto;
        }, organizationId);
        Map<String, TestAssetCategoryDTO> byId = new LinkedHashMap<>();
        rows.forEach(row -> byId.put(row.getId(), row));
        List<TestAssetCategoryDTO> roots = new ArrayList<>();
        rows.forEach(row -> {
            TestAssetCategoryDTO parent = byId.get(row.getParentId());
            if (parent == null) roots.add(row); else parent.getChildren().add(row);
        });
        String query = StringUtils.trimToNull(keyword);
        return query == null ? roots : filterTree(roots, query.toLowerCase(Locale.ROOT));
    }

    private List<TestAssetCategoryDTO> filterTree(List<TestAssetCategoryDTO> nodes, String keyword) {
        List<TestAssetCategoryDTO> result = new ArrayList<>();
        for (TestAssetCategoryDTO node : nodes) {
            List<TestAssetCategoryDTO> matchingChildren = filterTree(node.getChildren(), keyword);
            if (node.getName().toLowerCase(Locale.ROOT).contains(keyword) || !matchingChildren.isEmpty()) {
                node.setChildren(matchingChildren); result.add(node);
            }
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public TestAssetCategoryDTO create(TestAssetCategorySaveRequest request) {
        String organizationId = requireOrganization(); String user = SessionUtils.getUserId();
        String name = validName(request.getName()); Category parent = category(request.getParentId(), organizationId, false);
        int level = parent == null ? 1 : parent.level + 1;
        if (level > MAX_LEVEL) throw new MSException("资产分类最多支持 5 层");
        String id = IDGenerator.nextStr(); long now = System.currentTimeMillis();
        String parentId = parent == null ? "" : parent.id;
        String path = parent == null ? name : parent.path + " / " + name;
        Long sort = jdbc.queryForObject("SELECT COALESCE(MAX(sort),0)+5000 FROM test_asset_category WHERE organization_id=? AND parent_id=? AND deleted=b'0'", Long.class, organizationId, parentId);
        try {
            jdbc.update("INSERT INTO test_asset_category(id,organization_id,parent_id,name,normalized_name,path,level,sort,deleted,create_user,create_time,update_user,update_time) VALUES(?,?,?,?,?,?,?,?,b'0',?,?,?,?,?)",
                    id, organizationId, parentId, name, normalizeName(name), path, level, sort, user, now, user, now);
        } catch (DuplicateKeyException ex) {
            throw new MSException("同一父分类下名称不能重复");
        }
        audit(organizationId, null, "CATEGORY_CREATE", id, null, path, null);
        return findCategoryDto(id, organizationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TestAssetCategoryDTO update(String id, TestAssetCategorySaveRequest request) {
        String organizationId = requireOrganization(); Category current = category(id, organizationId, true);
        String name = validName(request.getName()); Category parent = category(request.getParentId(), organizationId, false);
        if (parent != null && (parent.id.equals(id) || parent.path.startsWith(current.path + " / "))) {
            throw new MSException("分类不能移动到自身或其子分类下");
        }
        int level = parent == null ? 1 : parent.level + 1;
        int subtreeDepth = jdbc.queryForObject("SELECT COALESCE(MAX(level),?)-? FROM test_asset_category WHERE organization_id=? AND deleted=b'0' AND (id=? OR path LIKE CONCAT(?, ' / %'))", Integer.class,
                current.level, current.level, organizationId, id, current.path);
        if (level + subtreeDepth > MAX_LEVEL) throw new MSException("移动后分类层级将超过 5 层");
        String parentId = parent == null ? "" : parent.id;
        String newPath = parent == null ? name : parent.path + " / " + name;
        long now = System.currentTimeMillis();
        try {
            jdbc.update("UPDATE test_asset_category SET parent_id=?,name=?,normalized_name=?,path=?,level=?,update_user=?,update_time=? WHERE id=? AND organization_id=? AND deleted=b'0'",
                    parentId, name, normalizeName(name), newPath, level, SessionUtils.getUserId(), now, id, organizationId);
        } catch (DuplicateKeyException ex) {
            throw new MSException("同一父分类下名称不能重复");
        }
        updateDescendantPaths(organizationId, id, current.path, newPath, level - current.level);
        audit(organizationId, null, "CATEGORY_UPDATE", id, current.path, newPath, null);
        return findCategoryDto(id, organizationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorder(TestAssetCategoryReorderRequest request) {
        String organizationId = requireOrganization();
        List<Category> categories = request.getIds().stream().map(id -> category(id, organizationId, true)).toList();
        if (categories.stream().map(c -> c.parentId).distinct().count() != 1) throw new MSException("只能调整同一父分类下的顺序");
        for (int i = 0; i < categories.size(); i++) jdbc.update("UPDATE test_asset_category SET sort=?,update_user=?,update_time=? WHERE id=? AND organization_id=?",
                (i + 1L) * 5000, SessionUtils.getUserId(), System.currentTimeMillis(), categories.get(i).id, organizationId);
        audit(organizationId, null, "CATEGORY_REORDER", categories.getFirst().parentId, null, JSON.toJSONString(request.getIds()), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, TestAssetCategoryDeleteRequest request) {
        String organizationId = requireOrganization(); Category current = category(id, organizationId, true);
        long children = jdbc.queryForObject("SELECT COUNT(1) FROM test_asset_category WHERE organization_id=? AND parent_id=? AND deleted=b'0'", Long.class, organizationId, id);
        long assets = jdbc.queryForObject("SELECT COUNT(1) FROM test_asset_metadata WHERE organization_id=? AND category_id=?", Long.class, organizationId, id);
        String strategy = StringUtils.upperCase(StringUtils.trimToEmpty(request.getStrategy()));
        if ((children > 0 || assets > 0) && !StringUtils.equalsAny(strategy, "MIGRATE", "UNCLASSIFY")) {
            throw new MSException("分类包含资产或子分类，请选择迁移或转为未分类");
        }
        Category target = category(request.getTargetCategoryId(), organizationId, false);
        if ("MIGRATE".equals(strategy) && target == null) throw new MSException("请选择迁移目标分类");
        if (target != null && (target.id.equals(id) || target.path.startsWith(current.path + " / "))) throw new MSException("不能迁移到待删除分类或其子分类");
        String targetId = target == null ? null : target.id;
        jdbc.update("UPDATE test_asset_metadata SET category_id=?,update_user=?,update_time=? WHERE organization_id=? AND category_id=?",
                targetId, SessionUtils.getUserId(), System.currentTimeMillis(), organizationId, id);
        String newParentId = "MIGRATE".equals(strategy) ? target.id : current.parentId;
        Category newParent = category(newParentId, organizationId, false);
        List<Category> directChildren = jdbc.query("SELECT id,parent_id,name,path,level FROM test_asset_category WHERE organization_id=? AND parent_id=? AND deleted=b'0'",
                this::mapCategory, organizationId, id);
        for (Category child : directChildren) {
            int newLevel = newParent == null ? 1 : newParent.level + 1;
            String newPath = newParent == null ? child.name : newParent.path + " / " + child.name;
            jdbc.update("UPDATE test_asset_category SET parent_id=?,path=?,level=?,update_user=?,update_time=? WHERE id=?", StringUtils.defaultString(newParentId), newPath, newLevel, SessionUtils.getUserId(), System.currentTimeMillis(), child.id);
            updateDescendantPaths(organizationId, child.id, child.path, newPath, newLevel - child.level);
        }
        jdbc.update("UPDATE test_asset_category SET deleted=b'1',normalized_name=CONCAT(normalized_name,'#deleted:',id),update_user=?,update_time=? WHERE id=? AND organization_id=?", SessionUtils.getUserId(), System.currentTimeMillis(), id, organizationId);
        audit(organizationId, null, "CATEGORY_DELETE", id, current.path, strategy + ":" + StringUtils.defaultString(targetId), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public TestAssetMetadataDTO metadata(String projectId, String assetType, String assetId) {
        catalogService.detail(projectId, assetType, assetId);
        String organizationId = requireProjectOrganization(projectId);
        ensureMetadata(organizationId, projectId, assetType, assetId);
        TestAssetMetadataDTO result = jdbc.queryForObject("""
                SELECT m.asset_type,m.asset_id,m.creation_source,m.category_id,c.name category_name,c.path category_path,
                       m.source_reference_type,m.source_reference_id,m.created_by_actor_type,m.created_by_actor_id,m.create_time
                FROM test_asset_metadata m LEFT JOIN test_asset_category c ON c.id=m.category_id AND c.deleted=b'0'
                WHERE m.organization_id=? AND m.project_id=? AND m.asset_type=? AND m.asset_id=?
                """, (rs, n) -> {
            TestAssetMetadataDTO dto = new TestAssetMetadataDTO();
            dto.setAssetType(rs.getString("asset_type")); dto.setAssetId(rs.getString("asset_id"));
            dto.setCreationSource(rs.getString("creation_source")); dto.setCategoryId(rs.getString("category_id"));
            dto.setCategoryName(rs.getString("category_name")); dto.setCategoryPath(rs.getString("category_path"));
            dto.setSourceReferenceType(rs.getString("source_reference_type")); dto.setSourceReferenceId(rs.getString("source_reference_id"));
            dto.setCreatedByActorType(rs.getString("created_by_actor_type")); dto.setCreatedByActorId(rs.getString("created_by_actor_id"));
            dto.setCreateTime(rs.getLong("create_time")); return dto;
        }, organizationId, projectId, assetType.toUpperCase(Locale.ROOT), assetId);
        if (result != null && "AI".equals(result.getCreationSource())
                && !SecurityUtils.getSubject().isPermitted(PermissionConstants.FUNCTIONAL_CASE_AI_READ)) {
            result.setSourceReferenceType(null);
            result.setSourceReferenceId(null);
        } else if (result != null && "AI".equals(result.getCreationSource())
                && "CASE".equalsIgnoreCase(result.getAssetType())) {
            attachAiCaseProvenance(result, projectId, assetId);
        }
        return result;
    }

    private void attachAiCaseProvenance(TestAssetMetadataDTO result, String projectId, String assetId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT g.id aiGenerationId,g.model_source_id aiModelId,ms.provider_name aiProvider,
                       ms.name aiModelName,c.system_prompt_version promptTemplateVersion,
                       d.source_document_id sourceDocumentId,g.create_time generationTime,
                       g.create_user generationInitiator,d.review_status reviewStatus,
                       d.reviewed_by reviewedBy,d.reviewed_at reviewedAt,
                       (SELECT MAX(h.create_time) FROM functional_case_ai_review_history h
                         WHERE h.draft_id=d.id AND h.action='PUBLISH') publishedAt
                FROM functional_case_ai_generation g
                JOIN functional_case_ai_draft d ON d.generation_id=g.id AND d.project_id=g.project_id
                LEFT JOIN functional_case fc ON fc.id=d.formal_case_id AND fc.project_id=d.project_id
                LEFT JOIN ai_model_source ms ON ms.id=g.model_source_id
                LEFT JOIN ai_case_conversation c ON c.id=g.conversation_id AND c.project_id=g.project_id
                WHERE g.project_id=? AND g.id=?
                  AND COALESCE(NULLIF(fc.ref_id,''),fc.id,d.formal_case_id)=?
                ORDER BY d.update_time DESC LIMIT 1
                """, projectId, result.getSourceReferenceId(), assetId);
        if (rows.isEmpty()) return;
        Map<String, Object> row = rows.getFirst();
        result.setAiGenerationId((String) row.get("aiGenerationId"));
        result.setAiModelId((String) row.get("aiModelId"));
        result.setAiProvider((String) row.get("aiProvider"));
        result.setAiModelName((String) row.get("aiModelName"));
        result.setPromptTemplateVersion((String) row.get("promptTemplateVersion"));
        result.setSourceDocumentId((String) row.get("sourceDocumentId"));
        result.setGenerationTime(number(row.get("generationTime")));
        result.setGenerationInitiator((String) row.get("generationInitiator"));
        result.setReviewStatus((String) row.get("reviewStatus"));
        result.setReviewedBy((String) row.get("reviewedBy"));
        result.setReviewedAt(number(row.get("reviewedAt")));
        result.setPublishedAt(number(row.get("publishedAt")));
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    @Transactional(rollbackFor = Exception.class)
    public TestAssetMetadataDTO assign(String assetType, String assetId, TestAssetCategoryAssignRequest request) {
        catalogService.detail(request.getProjectId(), assetType, assetId);
        String organizationId = requireProjectOrganization(request.getProjectId());
        Category target = category(request.getCategoryId(), organizationId, false);
        ensureMetadata(organizationId, request.getProjectId(), assetType, assetId);
        jdbc.update("UPDATE test_asset_metadata SET category_id=?,update_user=?,update_time=? WHERE organization_id=? AND project_id=? AND asset_type=? AND asset_id=?",
                target == null ? null : target.id, SessionUtils.getUserId(), System.currentTimeMillis(), organizationId, request.getProjectId(), assetType.toUpperCase(Locale.ROOT), assetId);
        audit(organizationId, request.getProjectId(), "ASSET_CATEGORY_ASSIGN", assetId, null, target == null ? null : target.path, null);
        return metadata(request.getProjectId(), assetType, assetId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<TestAssetBatchAssignResult> batchAssign(TestAssetBatchAssignRequest request) {
        List<TestAssetBatchAssignResult> results = new ArrayList<>();
        for (TestAssetBatchAssignRequest.Item item : request.getItems()) {
            try {
                TestAssetCategoryAssignRequest single = new TestAssetCategoryAssignRequest();
                single.setProjectId(item.getProjectId()); single.setCategoryId(request.getCategoryId());
                assign(item.getAssetType(), item.getAssetId(), single);
                results.add(new TestAssetBatchAssignResult(item.getProjectId(), item.getAssetType(), item.getAssetId(), true, "归类成功"));
            } catch (Exception ex) {
                results.add(new TestAssetBatchAssignResult(item.getProjectId(), item.getAssetType(), item.getAssetId(), false, "无权限、资产不存在或分类边界不匹配"));
            }
        }
        return results;
    }

    @Transactional(rollbackFor = Exception.class)
    public TestAssetMetadataDTO governSource(TestAssetSourceGovernanceRequest request) {
        String source = request.getCreationSource().toUpperCase(Locale.ROOT);
        if (!SOURCES.contains(source) || "UNKNOWN".equals(source)) throw new MSException("请选择有证据的确定来源");
        catalogService.detail(request.getProjectId(), request.getAssetType(), request.getAssetId());
        String organizationId = requireProjectOrganization(request.getProjectId());
        ensureMetadata(organizationId, request.getProjectId(), request.getAssetType(), request.getAssetId());
        int changed = jdbc.update("UPDATE test_asset_metadata SET creation_source=?,source_reference_type=?,source_reference_id=?,update_user=?,update_time=? WHERE organization_id=? AND project_id=? AND asset_type=? AND asset_id=? AND creation_source='UNKNOWN'",
                source, request.getSourceReferenceType(), request.getSourceReferenceId(), SessionUtils.getUserId(), System.currentTimeMillis(), organizationId, request.getProjectId(), request.getAssetType().toUpperCase(Locale.ROOT), request.getAssetId());
        if (changed == 0) throw new MSException("仅来源不明资产允许治理，已确认来源不可再次修改");
        audit(organizationId, request.getProjectId(), "SOURCE_GOVERN", request.getAssetId(), "UNKNOWN", source, request.getEvidence());
        return metadata(request.getProjectId(), request.getAssetType(), request.getAssetId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordTrustedSource(String projectId, String assetType, String assetId, String source,
                                    String referenceType, String referenceId, String actorType, String actorId) {
        if (!SOURCES.contains(source) || "UNKNOWN".equals(source)) throw new MSException("INVALID_TRUSTED_ASSET_SOURCE");
        String organizationId = projectOrganization(projectId); long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO test_asset_metadata(id,organization_id,project_id,asset_type,asset_id,creation_source,source_reference_type,source_reference_id,created_by_actor_type,created_by_actor_id,create_time,update_user,update_time)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  source_reference_type=IF(creation_source='UNKNOWN',VALUES(source_reference_type),source_reference_type),
                  source_reference_id=IF(creation_source='UNKNOWN',VALUES(source_reference_id),source_reference_id),
                  creation_source=IF(creation_source='UNKNOWN',VALUES(creation_source),creation_source),update_time=VALUES(update_time)
                """, IDGenerator.nextStr(), organizationId, projectId, assetType.toUpperCase(Locale.ROOT), assetId, source,
                referenceType, referenceId, actorType, actorId, now, actorId, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void copyCategoryByPath(String sourceProjectId, String sourceAssetType, String sourceAssetId,
                                   String targetProjectId, String targetAssetType, String targetAssetId,
                                   String actorId) {
        recordTrustedSource(targetProjectId, targetAssetType, targetAssetId, "IMPORT",
                "ASSET_COPY", sourceAssetId, "USER", actorId);
        String targetOrganizationId = projectOrganization(targetProjectId);
        List<String> paths = jdbc.query("""
                SELECT c.path FROM test_asset_metadata m
                JOIN test_asset_category c ON c.id=m.category_id AND c.deleted=b'0'
                WHERE m.project_id=? AND m.asset_type=? AND m.asset_id=? LIMIT 1
                """, (rs, n) -> rs.getString(1), sourceProjectId,
                sourceAssetType.toUpperCase(Locale.ROOT), sourceAssetId);
        String targetCategoryId = null;
        if (!paths.isEmpty()) {
            List<String> matches = jdbc.query("SELECT id FROM test_asset_category WHERE organization_id=? AND path=? AND deleted=b'0' LIMIT 1",
                    (rs, n) -> rs.getString(1), targetOrganizationId, paths.getFirst());
            targetCategoryId = matches.isEmpty() ? null : matches.getFirst();
        }
        jdbc.update("UPDATE test_asset_metadata SET category_id=?,update_user=?,update_time=? WHERE organization_id=? AND project_id=? AND asset_type=? AND asset_id=?",
                targetCategoryId, actorId, System.currentTimeMillis(), targetOrganizationId, targetProjectId,
                targetAssetType.toUpperCase(Locale.ROOT), targetAssetId);
        audit(targetOrganizationId, targetProjectId, "ASSET_COPY_CATEGORY_MATCH", targetAssetId,
                null, paths.isEmpty() ? "UNCLASSIFIED" : paths.getFirst(), sourceProjectId + ":" + sourceAssetId);
    }

    private void ensureMetadata(String organizationId, String projectId, String assetType, String assetId) {
        long now = System.currentTimeMillis();
        jdbc.update("INSERT IGNORE INTO test_asset_metadata(id,organization_id,project_id,asset_type,asset_id,creation_source,created_by_actor_type,create_time,update_time) VALUES(?,?,?,?,?,'UNKNOWN','SYSTEM',?,?)",
                IDGenerator.nextStr(), organizationId, projectId, assetType.toUpperCase(Locale.ROOT), assetId, now, now);
    }

    private String requireProjectOrganization(String projectId) {
        String organizationId = projectOrganization(projectId);
        String current = requireOrganization();
        if (!StringUtils.equals(organizationId, current)) throw new MSException("项目不存在或不属于当前组织");
        return current;
    }

    private String projectOrganization(String projectId) {
        List<String> ids = jdbc.query("SELECT organization_id FROM project WHERE id=?", (rs, n) -> rs.getString(1), projectId);
        if (ids.isEmpty()) throw new MSException("项目不存在");
        return ids.getFirst();
    }

    private String requireOrganization() {
        String id = SessionUtils.getCurrentOrganizationId();
        if (StringUtils.isBlank(id)) throw new MSException("请先选择组织");
        return id;
    }

    private String validName(String value) {
        String name = StringUtils.trimToEmpty(value);
        if (name.isEmpty() || name.length() > 100) throw new MSException("分类名称长度必须为 1～100 个字符");
        return name;
    }

    private String normalizeName(String name) { return name.toLowerCase(Locale.ROOT); }

    private Category category(String id, String organizationId, boolean required) {
        if (StringUtils.isBlank(id)) return null;
        List<Category> rows = jdbc.query("SELECT id,parent_id,name,path,level FROM test_asset_category WHERE id=? AND organization_id=? AND deleted=b'0'", this::mapCategory, id, organizationId);
        if (rows.isEmpty() && required) throw new MSException("资产分类不存在或已被删除");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Category mapCategory(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Category(rs.getString("id"), rs.getString("parent_id"), rs.getString("name"), rs.getString("path"), rs.getInt("level"));
    }

    private TestAssetCategoryDTO findCategoryDto(String id, String organizationId) {
        Category c = category(id, organizationId, true); TestAssetCategoryDTO dto = new TestAssetCategoryDTO();
        dto.setId(c.id); dto.setParentId(c.parentId); dto.setName(c.name); dto.setPath(c.path); dto.setLevel(c.level); return dto;
    }

    private void updateDescendantPaths(String organizationId, String rootId, String oldPath, String newPath, int levelDelta) {
        jdbc.update("UPDATE test_asset_category SET path=CONCAT(?,SUBSTRING(path,?)),level=level+?,update_user=?,update_time=? WHERE organization_id=? AND deleted=b'0' AND id<>? AND path LIKE CONCAT(?, ' / %')",
                newPath, oldPath.length() + 1, levelDelta, SessionUtils.getUserId(), System.currentTimeMillis(), organizationId, rootId, oldPath);
    }

    private void audit(String organizationId, String projectId, String event, String resourceId,
                       String before, String after, String evidence) {
        jdbc.update("INSERT INTO test_asset_governance_audit(id,organization_id,project_id,event_type,resource_id,before_value,after_value,evidence,operator,create_time) VALUES(?,?,?,?,?,?,?,?,?,?)",
                IDGenerator.nextStr(), organizationId, projectId, event, resourceId, before, after,
                StringUtils.abbreviate(evidence, 500), SessionUtils.getUserId(), System.currentTimeMillis());
    }

    private record Category(String id, String parentId, String name, String path, int level) {}
}
