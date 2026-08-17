package io.metersphere.functional.asset.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.functional.asset.dto.CaseAssetCatalogPageRequest;
import io.metersphere.functional.asset.dto.CaseAssetCatalogRequest;
import io.metersphere.functional.asset.dto.CaseAssetPageRequest;
import io.metersphere.functional.asset.dto.CaseAssetSaveRequest;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.dto.FunctionalCasePageDTO;
import io.metersphere.functional.dto.CaseCustomFieldDTO;
import io.metersphere.functional.dto.response.FunctionalCaseImportResponse;
import io.metersphere.functional.hub.service.DefaultHubModuleResolver;
import io.metersphere.functional.hub.service.DefaultHubCaseImportService;
import io.metersphere.functional.hub.dto.DefaultHubCaseImportRequest;
import io.metersphere.functional.hub.dto.DefaultHubJobResponse;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.request.FunctionalCaseAddRequest;
import io.metersphere.functional.request.FunctionalCaseDeleteRequest;
import io.metersphere.functional.request.FunctionalCaseEditRequest;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.functional.request.FunctionalCaseAssociationFileRequest;
import io.metersphere.functional.request.FunctionalCaseDeleteFileRequest;
import io.metersphere.functional.request.FunctionalCaseFileRequest;
import io.metersphere.functional.service.FunctionalCaseAttachmentService;
import io.metersphere.functional.service.FunctionalCaseFileService;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.project.service.ProjectTemplateService;
import io.metersphere.sdk.constants.DefaultHubConstants;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.constants.TemplateScene;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.sdk.TemplateDTO;
import io.metersphere.system.service.DefaultHubProjectService;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.PageUtils;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class CaseAssetService {
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private DefaultHubProjectService defaultHubProjectService;
    @Resource private DefaultHubModuleResolver defaultHubModuleResolver;
    @Resource private FunctionalCaseService functionalCaseService;
    @Resource private FunctionalCaseMapper functionalCaseMapper;
    @Resource private ProjectTemplateService projectTemplateService;
    @Resource private ProjectMapper projectMapper;
    @Resource private FunctionalCaseFileService functionalCaseFileService;
    @Resource private DefaultHubCaseImportService defaultHubCaseImportService;
    @Resource private CaseAssetImportJobService caseAssetImportJobService;
    @Resource private CaseAssetImportWorker caseAssetImportWorker;
    @Lazy
    @Resource private CaseAssetHistorySyncWorker caseAssetHistorySyncWorker;
    @Resource private FunctionalCaseAttachmentService functionalCaseAttachmentService;

    @Transactional(readOnly = true)
    public Map<String, Object> pageCatalogs(CaseAssetCatalogPageRequest request) {
        String orgId = requireOrganization();
        int pageSize = Math.min(Math.max(request.getPageSize(), 1), 100);
        int current = Math.max(request.getCurrent(), 1);
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        String filter = keyword.isEmpty() ? "" : " AND (name LIKE ? OR id LIKE ?)";
        List<Object> args = new ArrayList<>();
        args.add(orgId);
        if (!keyword.isEmpty()) {
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM case_asset_catalog WHERE organization_id = ? AND deleted = b'0'" + filter,
                Integer.class, args.toArray());
        args.add(pageSize);
        args.add((current - 1) * pageSize);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT c.id, c.name, c.source, c.hub_module_id hubModuleId, "
                        + "c.manually_renamed manuallyRenamed, c.create_time createTime, c.update_time updateTime, "
                        + "COUNT(DISTINCT r.project_id) relatedProjectCount FROM case_asset_catalog c "
                        + "LEFT JOIN case_asset_catalog_project_rel r ON r.catalog_id = c.id "
                        + "WHERE c.organization_id = ? AND c.deleted = b'0'" + filter
                        + " GROUP BY c.id ORDER BY c.update_time DESC LIMIT ? OFFSET ?", args.toArray());
        return Map.of("list", list, "total", total == null ? 0 : total, "current", current, "pageSize", pageSize);
    }

    public Map<String, Object> createCatalog(CaseAssetCatalogRequest request) {
        return upsertCatalog(requireOrganization(), request.getName(), "MANUAL", null, SessionUtils.getUserId());
    }

    public Map<String, Object> updateCatalog(CaseAssetCatalogRequest request) {
        Map<String, Object> catalog = requireCatalog(request.getId());
        String name = cleanName(request.getName());
        long now = System.currentTimeMillis();
        try {
            jdbcTemplate.update("UPDATE case_asset_catalog SET name = ?, normalized_name = ?, manually_renamed = b'1', "
                            + "update_user = ?, update_time = ? WHERE id = ? AND deleted = b'0'",
                    name, normalize(name), SessionUtils.getUserId(), now, request.getId());
        } catch (DuplicateKeyException e) {
            throw new MSException("当前组织已存在同名用例资产目录");
        }
        jdbcTemplate.update("UPDATE functional_case_module SET name = ?, update_user = ?, update_time = ? WHERE id = ?",
                name, SessionUtils.getUserId(), now, catalog.get("hubModuleId"));
        return requireCatalog(request.getId());
    }

    public void deleteCatalog(String catalogId) {
        Map<String, Object> catalog = requireCatalog(catalogId);
        List<String> modules = defaultHubModuleResolver.listDescendantModuleIds(requireHubProjectId(),
                List.of(String.valueOf(catalog.get("hubModuleId"))));
        if (modules.isEmpty()) throw new MSException("资产目录存储节点不存在");
        String placeholders = modules.stream().map(id -> "?").collect(Collectors.joining(","));
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM functional_case WHERE module_id IN (" + placeholders
                + ") AND deleted = b'0'", Integer.class, modules.toArray());
        if (count != null && count > 0) throw new MSException("目录内存在资产用例，请先处理用例");
        long now = System.currentTimeMillis();
        jdbcTemplate.update("UPDATE case_asset_catalog SET deleted=b'1', delete_user=?, delete_time=?, update_time=? WHERE id=?",
                SessionUtils.getUserId(), now, now, catalogId);
        jdbcTemplate.update("DELETE FROM case_asset_catalog_project_rel WHERE catalog_id = ?", catalogId);
    }

    @Transactional(readOnly = true)
    public Pager<List<FunctionalCasePageDTO>> pageCases(CaseAssetPageRequest request) {
        validateSelectorContext(request.getTargetProjectId(), request.getScene());
        Map<String, Object> catalog = requireCatalog(request.getCatalogId());
        String hubProjectId = requireHubProjectId();
        List<String> modules = defaultHubModuleResolver.listDescendantModuleIds(hubProjectId,
                List.of(String.valueOf(catalog.get("hubModuleId"))));
        request.setProjectId(hubProjectId);
        request.setModuleIds(modules);
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize(),
                StringUtils.defaultIfBlank(request.getSortString(), "functional_case.update_time desc"));
        List<FunctionalCasePageDTO> rows = functionalCaseService.getFunctionalCasePage(request, false, true);
        attachReferencedProjects(rows);
        return PageUtils.setPageInfo(page, rows);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> caseOptions(List<String> ids, String targetProjectId, String scene) {
        validateSelectorContext(targetProjectId, scene);
        if (ids == null || ids.isEmpty()) return List.of();
        List<String> distinctIds = ids.stream().filter(StringUtils::isNotBlank).distinct().limit(500).toList();
        if (distinctIds.isEmpty()) return List.of();
        List<String> roots = jdbcTemplate.queryForList("SELECT hub_module_id FROM case_asset_catalog "
                + "WHERE organization_id=? AND deleted=b'0'", String.class, requireOrganization());
        List<String> modules = defaultHubModuleResolver.listDescendantModuleIds(requireHubProjectId(), roots);
        if (modules.isEmpty()) return List.of();
        String idMarks = distinctIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String moduleMarks = modules.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>(distinctIds);
        args.addAll(modules);
        args.add(requireHubProjectId());
        return jdbcTemplate.queryForList("SELECT id, name, num, module_id moduleId FROM functional_case WHERE id IN ("
                + idMarks + ") AND module_id IN (" + moduleMarks + ") AND project_id=? AND deleted=b'0'", args.toArray());
    }

    private void validateSelectorContext(String targetProjectId, String scene) {
        if (StringUtils.isBlank(targetProjectId) && StringUtils.isBlank(scene)) return;
        if (StringUtils.isBlank(targetProjectId)) throw new MSException("资产选择器缺少目标项目");
        Project project = projectMapper.selectByPrimaryKey(targetProjectId);
        if (project == null || Boolean.TRUE.equals(project.getDeleted())
                || !StringUtils.equals(project.getOrganizationId(), requireOrganization())) {
            throw new MSException("资产选择器目标项目不存在或跨组织");
        }
        boolean canImport = SessionUtils.hasPermission(null, targetProjectId, PermissionConstants.FUNCTIONAL_CASE_READ_IMPORT)
                || SessionUtils.hasPermission(null, targetProjectId, PermissionConstants.FUNCTIONAL_CASE_READ_ADD);
        if (!canImport) throw new MSException("无权向目标项目导入用例");
        if (StringUtils.equals(scene, "SCHEDULE_RULE")
                && !SessionUtils.hasPermission(null, targetProjectId, PermissionConstants.AI_EXECUTION_RUN)) {
            throw new MSException("无权为目标项目配置调度规则");
        }
    }

    public FunctionalCase addCase(CaseAssetSaveRequest request) {
        Map<String, Object> catalog = requireCatalog(request.getCatalogId());
        String hubProjectId = requireHubProjectId();
        TemplateDTO template = projectTemplateService.getDefaultTemplateDTO(hubProjectId, TemplateScene.FUNCTIONAL.name());
        FunctionalCaseAddRequest add = new FunctionalCaseAddRequest();
        copySaveFields(request, add);
        add.setProjectId(hubProjectId);
        add.setWorkspaceId(requireOrganization());
        add.setModuleId(String.valueOf(catalog.get("hubModuleId")));
        add.setTemplateId(template.getId());
        return functionalCaseService.addFunctionalCase(add, List.of(), SessionUtils.getUserId(), requireOrganization());
    }

    public FunctionalCase updateCase(CaseAssetSaveRequest request) {
        requireAssetCase(request.getId(), request.getCatalogId(), false);
        Map<String, Object> catalog = requireCatalog(request.getCatalogId());
        String hubProjectId = requireHubProjectId();
        FunctionalCase current = functionalCaseMapper.selectByPrimaryKey(request.getId());
        FunctionalCaseEditRequest edit = new FunctionalCaseEditRequest();
        copySaveFields(request, edit);
        edit.setId(request.getId());
        edit.setProjectId(hubProjectId);
        edit.setModuleId(String.valueOf(catalog.get("hubModuleId")));
        edit.setTemplateId(current.getTemplateId());
        edit.setVersionId(current.getVersionId());
        return functionalCaseService.updateFunctionalCase(edit, List.of(), SessionUtils.getUserId());
    }

    @Transactional(readOnly = true)
    public FunctionalCaseDetailDTO detail(String catalogId, String caseId) {
        requireAssetCase(caseId, catalogId, true);
        return functionalCaseService.getFunctionalCaseDetail(caseId, SessionUtils.getUserId(), true, true);
    }

    public void uploadAttachment(String catalogId, String caseId, MultipartFile file) {
        requireAssetCase(caseId, catalogId, false);
        FunctionalCaseAssociationFileRequest request = new FunctionalCaseAssociationFileRequest();
        request.setCaseId(caseId);
        request.setProjectId(requireHubProjectId());
        functionalCaseAttachmentService.uploadOrAssociationFile(request, file, SessionUtils.getUserId());
    }

    public void deleteAttachment(String catalogId, String caseId, String fileId, Boolean local) {
        requireAssetCase(caseId, catalogId, false);
        FunctionalCaseDeleteFileRequest request = new FunctionalCaseDeleteFileRequest();
        request.setCaseId(caseId);
        request.setProjectId(requireHubProjectId());
        request.setId(fileId);
        request.setLocal(local);
        functionalCaseAttachmentService.deleteFile(request, SessionUtils.getUserId());
    }

    public ResponseEntity<byte[]> downloadAttachment(String catalogId, String caseId, String fileId, Boolean local) throws Exception {
        requireAssetCase(caseId, catalogId, true);
        FunctionalCaseFileRequest request = new FunctionalCaseFileRequest();
        request.setProjectId(requireHubProjectId());
        request.setCaseId(caseId);
        request.setFileId(fileId);
        request.setLocal(local);
        return functionalCaseAttachmentService.downloadPreviewImgById(request);
    }

    public void deleteCase(String catalogId, String caseId) {
        requireAssetCase(caseId, catalogId, false);
        FunctionalCaseDeleteRequest request = new FunctionalCaseDeleteRequest();
        request.setId(caseId);
        request.setProjectId(requireHubProjectId());
        request.setDeleteAll(true);
        functionalCaseService.deleteFunctionalCase(request, SessionUtils.getUserId());
    }

    public Map<String, Object> importExcel(String catalogId, FunctionalCaseImportRequest request, MultipartFile file) {
        return submitFileImport(catalogId, request, file, "excel");
    }

    public Map<String, Object> importXMind(String catalogId, FunctionalCaseImportRequest request, MultipartFile file) {
        return submitFileImport(catalogId, request, file, "xmind");
    }

    private Map<String, Object> submitFileImport(String catalogId, FunctionalCaseImportRequest request,
                                                 MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            throw new MSException("导入文件不能为空");
        }
        String fileName = StringUtils.lowerCase(StringUtils.defaultString(file.getOriginalFilename()));
        boolean validExtension = "xmind".equalsIgnoreCase(type) ? fileName.endsWith(".xmind")
                : fileName.endsWith(".xlsx") || fileName.endsWith(".xls");
        if (!validExtension) {
            throw new MSException("xmind".equalsIgnoreCase(type)
                    ? "请选择 .xmind 格式文件" : "请选择 .xlsx 或 .xls 格式文件");
        }
        if (file.getSize() > 50L * 1024 * 1024) {
            throw new MSException("导入文件不能超过 50 MB");
        }
        prepareImport(catalogId, request);
        String jobId = caseAssetImportJobService.create(catalogId, file, request.isCover() ? "OVERWRITE" : "SKIP");
        try {
            caseAssetImportWorker.submit(jobId, type, request, SessionUtils.getUser(), file.getOriginalFilename(),
                    file.getContentType(), file.getBytes());
            return Map.of("jobId", jobId, "status", "RUNNING");
        } catch (Exception e) {
            RuntimeException failure = e instanceof RuntimeException runtime ? runtime : new MSException(e.getMessage());
            caseAssetImportJobService.fail(jobId, failure);
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fileImportJob(String jobId) {
        return caseAssetImportJobService.get(jobId, requireOrganization());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> latestFileImportJob(String catalogId) {
        requireCatalog(catalogId);
        return caseAssetImportJobService.getLatest(catalogId, requireOrganization());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadFileImportErrors(String jobId) {
        Map<String, Object> job = caseAssetImportJobService.get(jobId, requireOrganization());
        String detail = job.get("errorDetail") == null ? null : String.valueOf(job.get("errorDetail"));
        if (StringUtils.isBlank(detail)) {
            throw new MSException("该导入任务没有可下载的错误明细");
        }
        String content = "\uFEFF导入任务: " + jobId + System.lineSeparator()
                + "文件: " + StringUtils.defaultString((String) job.get("fileName"), "-") + System.lineSeparator()
                + "状态: " + job.get("status") + System.lineSeparator()
                + "成功: " + job.get("successCount") + "，失败: " + job.get("failCount") + System.lineSeparator()
                + System.lineSeparator() + detail;
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=case-asset-import-errors-" + jobId + ".txt")
                .body(bytes);
    }

    public DefaultHubJobResponse importToProject(DefaultHubCaseImportRequest request) {
        Project target = projectMapper.selectByPrimaryKey(request.getTargetProjectId());
        if (target == null || Boolean.TRUE.equals(target.getDeleted())
                || !StringUtils.equals(target.getOrganizationId(), requireOrganization())) {
            throw new MSException("目标项目不存在或不属于当前组织");
        }
        if (!SessionUtils.hasPermission(null, target.getId(), PermissionConstants.FUNCTIONAL_CASE_READ_IMPORT)
                && !SessionUtils.hasPermission(null, target.getId(), PermissionConstants.FUNCTIONAL_CASE_READ_ADD)) {
            throw new MSException("无权向目标项目导入用例");
        }
        if (!StringUtils.equals(request.getSelectMode(), DefaultHubConstants.SELECT_CASE_IDS)
                || request.getIds() == null || request.getIds().isEmpty()) {
            throw new MSException("请选择要导入的资产用例");
        }
        List<String> rootModules = jdbcTemplate.queryForList("SELECT hub_module_id FROM case_asset_catalog "
                + "WHERE organization_id = ? AND deleted = b'0'", String.class, requireOrganization());
        List<String> allowedModules = defaultHubModuleResolver.listDescendantModuleIds(requireHubProjectId(), rootModules);
        if (allowedModules.isEmpty()) throw new MSException("当前组织没有可用的用例资产");
        String casePlaceholders = request.getIds().stream().map(id -> "?").collect(Collectors.joining(","));
        String modulePlaceholders = allowedModules.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>(request.getIds());
        args.addAll(allowedModules);
        Integer valid = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT id) FROM functional_case WHERE id IN (" + casePlaceholders
                + ") AND module_id IN (" + modulePlaceholders + ") AND project_id = ? AND deleted = b'0'",
                Integer.class, concatArgs(args, requireHubProjectId()));
        if (valid == null || valid != request.getIds().stream().distinct().count()) {
            throw new MSException("选中内容包含已删除、跨组织或非资产用例");
        }
        return defaultHubCaseImportService.startImport(request, SessionUtils.getUserId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> importResult(String jobId) {
        Integer jobCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM default_hub_sync_job j JOIN project p "
                + "ON p.id=j.scope_project_id WHERE j.id=? AND p.organization_id=?", Integer.class, jobId, requireOrganization());
        if (jobCount == null || jobCount == 0) throw new MSException("资产导入任务不存在");
        return defaultHubCaseImportService.getImportResults(jobId);
    }

    public void upsertForProject(String projectId) {
        Project project = projectMapper.selectByPrimaryKey(projectId);
        if (project == null || Boolean.TRUE.equals(project.getDeleted()) || defaultHubProjectService.isDefaultProject(projectId)) return;
        upsertCatalog(project.getOrganizationId(), project.getName(), "PROJECT", projectId, "system");
    }

    public void unlinkProject(String projectId) {
        jdbcTemplate.update("DELETE FROM case_asset_catalog_project_rel WHERE project_id = ?", projectId);
    }

    public Map<String, Object> backfillProjectCatalogs() {
        String organizationId = requireOrganization();
        String operator = SessionUtils.getUserId();
        List<String> runningJobs = jdbcTemplate.queryForList("SELECT id FROM case_asset_history_sync_job "
                        + "WHERE organization_id=? AND status IN ('PENDING','RUNNING') ORDER BY create_time DESC LIMIT 1",
                String.class, organizationId);
        if (!runningJobs.isEmpty()) {
            return getHistorySyncJob(runningJobs.getFirst());
        }
        List<String> projectIds = jdbcTemplate.queryForList("SELECT id FROM project WHERE organization_id = ? AND deleted = b'0'",
                String.class, organizationId);
        String jobId = IDGenerator.nextStr();
        long now = System.currentTimeMillis();
        jdbcTemplate.update("INSERT INTO case_asset_history_sync_job (id,organization_id,status,total_count,create_user," +
                        "create_time,update_time) VALUES (?,?,'PENDING',?,?,?,?)",
                jobId, organizationId, projectIds.size(), operator, now, now);
        for (String projectId : projectIds) {
            jdbcTemplate.update("INSERT INTO case_asset_history_sync_item (id,job_id,project_id,status,create_time,update_time) " +
                    "VALUES (?,?,?,'PENDING',?,?)", IDGenerator.nextStr(), jobId, projectId, now, now);
        }
        runAfterCommit(() -> caseAssetHistorySyncWorker.execute(jobId, organizationId, operator));
        return Map.of("jobId", jobId, "status", "PENDING", "total", projectIds.size());
    }

    public Map<String, Integer> syncHistoricalProject(String projectId, String organizationId, String operator) {
        Project sourceProject = projectMapper.selectByPrimaryKey(projectId);
        if (sourceProject == null || !StringUtils.equals(sourceProject.getOrganizationId(), organizationId)
                || defaultHubProjectService.isDefaultProject(projectId)) {
            return Map.of("created", 0, "updated", 0, "skipped", 1);
        }
        Map<String, Object> catalog = upsertCatalog(organizationId, sourceProject.getName(), "PROJECT", projectId, operator);
        return syncHistoricalCases(sourceProject, catalog, organizationId, operator);
    }

    private Map<String, Integer> syncHistoricalCases(Project sourceProject, Map<String, Object> catalog,
                                                     String organizationId, String operator) {
        String hubProjectId = requireHubProjectId();
        if (StringUtils.equals(sourceProject.getId(), hubProjectId)) return Map.of("created", 0, "updated", 0, "skipped", 0);
        List<Map<String, Object>> sources = jdbcTemplate.queryForList("SELECT fc.id,COALESCE(NULLIF(fc.ref_id,''),fc.id) sourceRefId,"
                + "fc.name,fc.case_edit_type caseEditType,"
                + "fc.tags,fc.update_time updateTime,rel.asset_case_id assetCaseId,rel.source_update_time sourceSyncedTime "
                + "FROM functional_case fc LEFT JOIN case_asset_source_relation rel "
                + "ON rel.source_project_id=fc.project_id "
                + "AND rel.source_case_id=COALESCE(NULLIF(fc.ref_id,''),fc.id) "
                + "WHERE fc.project_id=? AND fc.deleted=b'0' AND fc.latest=b'1' "
                + "AND (rel.id IS NULL OR COALESCE(fc.update_time,0)>COALESCE(rel.source_update_time,0)) "
                + "ORDER BY fc.create_time,fc.id", sourceProject.getId());
        TemplateDTO template = projectTemplateService.getDefaultTemplateDTO(hubProjectId, TemplateScene.FUNCTIONAL.name());
        int created = 0;
        int updated = 0;
        for (Map<String, Object> source : sources) {
            String sourceCaseId = String.valueOf(source.get("id"));
            String sourceRefId = String.valueOf(source.get("sourceRefId"));
            List<Map<String, Object>> blobs = jdbcTemplate.queryForList("SELECT prerequisite,steps,text_description textDescription,"
                    + "expected_result expectedResult,description FROM functional_case_blob WHERE id=?", sourceCaseId);
            Map<String, Object> blob = blobs.isEmpty() ? Map.of() : blobs.getFirst();
            FunctionalCase sourceCase = functionalCaseMapper.selectByPrimaryKey(sourceCaseId);
            FunctionalCaseAddRequest add = new FunctionalCaseAddRequest();
            add.setProjectId(hubProjectId);
            add.setWorkspaceId(organizationId);
            add.setModuleId(String.valueOf(catalog.get("hubModuleId")));
            add.setTemplateId(template.getId());
            add.setName(String.valueOf(source.get("name")));
            add.setCaseEditType(StringUtils.defaultIfBlank((String) source.get("caseEditType"), "STEP"));
            add.setPrerequisite(blobText(blob.get("prerequisite")));
            add.setSteps(blobText(blob.get("steps")));
            add.setTextDescription(blobText(blob.get("textDescription")));
            add.setExpectedResult(blobText(blob.get("expectedResult")));
            add.setDescription(blobText(blob.get("description")));
            add.setTags(sourceCase == null ? List.of() : sourceCase.getTags());
            List<CaseCustomFieldDTO> customFields = jdbcTemplate.query(
                    "SELECT field_id,value FROM functional_case_custom_field WHERE case_id=?",
                    (rs, rowNum) -> {
                        CaseCustomFieldDTO field = new CaseCustomFieldDTO();
                        field.setFieldId(rs.getString("field_id"));
                        field.setValue(rs.getString("value"));
                        return field;
                    }, sourceCaseId);
            if (!customFields.isEmpty()) {
                List<String> targetFieldIds = template.getCustomFields() == null ? List.of()
                        : template.getCustomFields().stream().map(item -> item.getFieldId()).toList();
                add.setCustomFields(customFields.stream().filter(field -> targetFieldIds.contains(field.getFieldId())).toList());
            }
            add.setPublicCase("false");
            String existingAssetCaseId = source.get("assetCaseId") == null ? null : String.valueOf(source.get("assetCaseId"));
            FunctionalCase current = StringUtils.isBlank(existingAssetCaseId)
                    ? null : functionalCaseMapper.selectByPrimaryKey(existingAssetCaseId);
            FunctionalCase asset;
            if (current == null || Boolean.TRUE.equals(current.getDeleted())) {
                asset = functionalCaseService.addFunctionalCase(add, List.of(), operator, organizationId);
            } else {
                FunctionalCaseEditRequest edit = new FunctionalCaseEditRequest();
                org.springframework.beans.BeanUtils.copyProperties(add, edit);
                edit.setId(existingAssetCaseId);
                edit.setVersionId(current.getVersionId());
                asset = functionalCaseService.updateFunctionalCase(edit, List.of(), operator);
            }
            long now = System.currentTimeMillis();
            jdbcTemplate.update("INSERT INTO case_asset_source_relation (id,asset_case_id,source_project_id,source_case_id," +
                            "source_update_time,create_user,create_time,update_time) VALUES (?,?,?,?,?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE asset_case_id=VALUES(asset_case_id),source_update_time=VALUES(source_update_time),update_time=VALUES(update_time)",
                    IDGenerator.nextStr(), asset.getId(), sourceProject.getId(), sourceRefId, source.get("updateTime"), operator, now, now);
            jdbcTemplate.update("INSERT IGNORE INTO case_asset_lineage (id,source_case_id,target_case_id,target_project_id,"
                            + "import_batch_id,conflict_strategy,create_user,create_time,update_time) VALUES (?,?,?,?,NULL,'HISTORY_BACKFILL',?,?,?)",
                    IDGenerator.nextStr(), asset.getId(), sourceCaseId, sourceProject.getId(), operator, now, now);
            if (current == null || Boolean.TRUE.equals(current.getDeleted())) created++; else updated++;
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM functional_case WHERE project_id=? AND deleted=b'0' AND latest=b'1'",
                Integer.class, sourceProject.getId());
        return Map.of("created", created, "updated", updated,
                "skipped", Math.max(0, (total == null ? 0 : total) - created - updated));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getHistorySyncJob(String jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id jobId,status,total_count total,success_count success," +
                        "skipped_count skipped,failed_count failed,case_created_count caseCreated,case_updated_count caseUpdated," +
                        "case_skipped_count caseSkipped,create_time createTime,update_time updateTime,finish_time finishTime " +
                        "FROM case_asset_history_sync_job WHERE id=? AND organization_id=?", jobId, requireOrganization());
        if (rows.isEmpty()) throw new MSException("历史用例同步任务不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put("items", jdbcTemplate.queryForList("SELECT project_id projectId,status,case_created_count caseCreated," +
                "case_updated_count caseUpdated,case_skipped_count caseSkipped,failure_reason failureReason " +
                "FROM case_asset_history_sync_item WHERE job_id=? ORDER BY create_time,project_id", jobId));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLatestHistorySyncJob() {
        List<String> ids = jdbcTemplate.queryForList("SELECT id FROM case_asset_history_sync_job "
                        + "WHERE organization_id=? ORDER BY create_time DESC LIMIT 1",
                String.class, requireOrganization());
        if (ids.isEmpty()) return Map.of("exists", false);
        Map<String, Object> result = getHistorySyncJob(ids.getFirst());
        result.put("exists", true);
        return result;
    }

    public Map<String, Object> retryHistorySyncJob(String jobId) {
        Map<String, Object> job = getHistorySyncJob(jobId);
        String status = String.valueOf(job.get("status"));
        if (!StringUtils.equalsAny(status, "FAILED", "PARTIAL_SUCCESS")) {
            throw new MSException("仅失败或部分成功的历史同步任务可以重试");
        }
        jdbcTemplate.update("UPDATE case_asset_history_sync_item SET status='PENDING',failure_reason=NULL,update_time=? " +
                "WHERE job_id=? AND status='FAILED'", System.currentTimeMillis(), jobId);
        jdbcTemplate.update("UPDATE case_asset_history_sync_job SET status='PENDING',failed_count=0,finish_time=NULL,update_time=? WHERE id=?",
                System.currentTimeMillis(), jobId);
        String organizationId = requireOrganization();
        String operator = SessionUtils.getUserId();
        runAfterCommit(() -> caseAssetHistorySyncWorker.execute(jobId, organizationId, operator));
        return getHistorySyncJob(jobId);
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private String blobText(Object value) {
        if (value == null) return StringUtils.EMPTY;
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return String.valueOf(value);
    }

    private Map<String, Object> upsertCatalog(String orgId, String rawName, String source, String projectId, String operator) {
        String name = cleanName(rawName);
        String normalized = normalize(name);
        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT id, name, hub_module_id hubModuleId FROM case_asset_catalog "
                + "WHERE organization_id = ? AND normalized_name = ? AND deleted = b'0' LIMIT 1", orgId, normalized);
        Map<String, Object> catalog;
        if (existing.isEmpty()) {
            String hubProjectId = requireHubProjectId();
            String moduleId = IDGenerator.nextStr();
            String catalogId = IDGenerator.nextStr();
            long now = System.currentTimeMillis();
            try {
                jdbcTemplate.update("INSERT INTO functional_case_module (id, project_id, name, parent_id, module_type, ref_project_id, pos, "
                                + "create_time, update_time, create_user, update_user) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?)",
                        moduleId, hubProjectId, name, ModuleConstants.ROOT_NODE_PARENT_ID, DefaultHubConstants.MODULE_TYPE_FOLDER,
                        now, now, now, operator, operator);
                jdbcTemplate.update("INSERT INTO case_asset_catalog (id, organization_id, name, normalized_name, hub_module_id, source, "
                                + "manually_renamed, deleted, create_user, update_user, create_time, update_time) "
                                + "VALUES (?, ?, ?, ?, ?, ?, b'0', b'0', ?, ?, ?, ?)",
                        catalogId, orgId, name, normalized, moduleId, source, operator, operator, now, now);
                catalog = Map.of("id", catalogId, "name", name, "hubModuleId", moduleId);
            } catch (DuplicateKeyException e) {
                existing = jdbcTemplate.queryForList("SELECT id, name, hub_module_id hubModuleId FROM case_asset_catalog "
                        + "WHERE organization_id = ? AND normalized_name = ? AND deleted = b'0' LIMIT 1", orgId, normalized);
                if (existing.isEmpty()) throw e;
                catalog = existing.getFirst();
            }
        } else {
            catalog = existing.getFirst();
        }
        if (StringUtils.isNotBlank(projectId)) {
            jdbcTemplate.update("INSERT IGNORE INTO case_asset_catalog_project_rel "
                            + "(id, catalog_id, project_id, relation_type, create_user, create_time) VALUES (?, ?, ?, 'NAME_MATCH', ?, ?)",
                    IDGenerator.nextStr(), catalog.get("id"), projectId, operator, System.currentTimeMillis());
        }
        return catalog;
    }

    private void prepareImport(String catalogId, FunctionalCaseImportRequest request) {
        Map<String, Object> catalog = requireCatalog(catalogId);
        request.setProjectId(requireHubProjectId());
        request.setWorkspaceId(requireOrganization());
        request.setModuleId(String.valueOf(catalog.get("hubModuleId")));
    }

    private Map<String, Object> requireCatalog(String id) {
        if (StringUtils.isBlank(id)) throw new MSException("用例资产目录 ID 不能为空");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, name, hub_module_id hubModuleId, source, manually_renamed manuallyRenamed "
                + "FROM case_asset_catalog WHERE id = ? AND organization_id = ? AND deleted = b'0'", id, requireOrganization());
        if (rows.isEmpty()) throw new MSException("用例资产目录不存在或无权访问");
        return rows.getFirst();
    }

    private FunctionalCase requireAssetCase(String caseId, String catalogId, boolean includeDeleted) {
        Map<String, Object> catalog = requireCatalog(catalogId);
        FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(caseId);
        if (functionalCase == null || (!includeDeleted && Boolean.TRUE.equals(functionalCase.getDeleted()))) {
            throw new MSException("资产用例不存在或已删除");
        }
        List<String> moduleIds = defaultHubModuleResolver.listDescendantModuleIds(requireHubProjectId(),
                List.of(String.valueOf(catalog.get("hubModuleId"))));
        if (!StringUtils.equals(functionalCase.getProjectId(), requireHubProjectId()) || !moduleIds.contains(functionalCase.getModuleId())) {
            throw new MSException("资产用例不属于当前目录");
        }
        return functionalCase;
    }

    private void attachReferencedProjects(List<FunctionalCasePageDTO> rows) {
        if (rows.isEmpty()) return;
        String placeholders = rows.stream().map(item -> "?").collect(Collectors.joining(","));
        List<Object> ids = rows.stream().map(FunctionalCasePageDTO::getId).collect(Collectors.toList());
        List<Map<String, Object>> refs = jdbcTemplate.queryForList("SELECT DISTINCT l.source_case_id sourceCaseId, p.id, p.name "
                + "FROM case_asset_lineage l JOIN test_plan_functional_case tpc ON tpc.functional_case_id = l.target_case_id "
                + "JOIN test_plan tp ON tp.id = tpc.test_plan_id AND tp.deleted = b'0' "
                + "JOIN project p ON p.id = tp.project_id AND p.deleted = b'0' "
                + "WHERE l.source_case_id IN (" + placeholders + ")", ids.toArray());
        Map<String, List<Map<String, Object>>> grouped = refs.stream().collect(Collectors.groupingBy(
                row -> String.valueOf(row.get("sourceCaseId")), LinkedHashMap::new, Collectors.toList()));
        rows.forEach(row -> {
            List<Map<String, Object>> projects = grouped.getOrDefault(row.getId(), List.of());
            row.setReferencedProjectCount(projects.size());
            row.setReferencedProjects(projects.stream().limit(3).toList());
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> referencedProjects(String catalogId, String caseId, int current, int pageSize) {
        requireAssetCase(caseId, catalogId, true);
        int safeCurrent = Math.max(current, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        String base = " FROM (SELECT DISTINCT p.id, p.name FROM case_asset_lineage l "
                + "JOIN test_plan_functional_case tpc ON tpc.functional_case_id=l.target_case_id "
                + "JOIN test_plan tp ON tp.id=tpc.test_plan_id AND tp.deleted=b'0' "
                + "JOIN project p ON p.id=tp.project_id AND p.deleted=b'0' "
                + "WHERE l.source_case_id=? AND p.organization_id=?) refs";
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + base, Integer.class, caseId, requireOrganization());
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT id, name" + base + " ORDER BY name LIMIT ? OFFSET ?",
                caseId, requireOrganization(), safePageSize, (safeCurrent - 1) * safePageSize);
        return Map.of("list", list, "total", total == null ? 0 : total, "current", safeCurrent, "pageSize", safePageSize);
    }

    private void copySaveFields(CaseAssetSaveRequest source, FunctionalCaseAddRequest target) {
        target.setName(source.getName());
        target.setCaseEditType(StringUtils.defaultIfBlank(source.getCaseEditType(), "STEP"));
        target.setPrerequisite(StringUtils.defaultString(source.getPrerequisite()));
        target.setSteps(StringUtils.defaultString(source.getSteps()));
        target.setTextDescription(StringUtils.defaultString(source.getTextDescription()));
        target.setExpectedResult(StringUtils.defaultString(source.getExpectedResult()));
        target.setDescription(StringUtils.defaultString(source.getDescription()));
        target.setTags(source.getTags());
        target.setCustomFields(source.getCustomFields());
        target.setAttachments(source.getAttachments());
        target.setPublicCase("false");
    }

    private String requireHubProjectId() {
        String id = defaultHubProjectService.getDefaultProjectId();
        if (StringUtils.isBlank(id)) throw new MSException("用例资产底层存储项目未配置");
        return id;
    }

    private String requireOrganization() {
        String id = SessionUtils.getCurrentOrganizationId();
        if (StringUtils.isBlank(id)) throw new MSException("请先进入组织");
        return id;
    }

    private String cleanName(String value) {
        String name = StringUtils.trimToEmpty(value).replaceAll("\\s+", " ");
        if (name.isEmpty()) throw new MSException("用例项目名称不能为空");
        if (name.length() > 255) throw new MSException("用例项目名称不能超过 255 个字符");
        return name;
    }

    private String normalize(String value) {
        return cleanName(value).toLowerCase(Locale.ROOT);
    }

    private Object[] concatArgs(List<Object> values, Object last) {
        List<Object> result = new ArrayList<>(values);
        result.add(last);
        return result.toArray();
    }
}
