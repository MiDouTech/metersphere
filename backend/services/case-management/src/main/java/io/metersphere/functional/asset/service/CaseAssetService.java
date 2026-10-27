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
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class CaseAssetService {
    static final String REFERENCED_PROJECTS_FROM_SQL = " FROM case_asset_lineage l "
            + "JOIN test_plan_functional_case tpc ON tpc.functional_case_id = l.target_case_id "
            + "JOIN test_plan tp ON tp.id = tpc.test_plan_id "
            + "JOIN project p ON p.id = tp.project_id AND p.deleted = b'0' ";

    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private DefaultHubProjectService defaultHubProjectService;
    @Resource @org.springframework.context.annotation.Lazy private io.metersphere.functional.hub.service.DefaultHubCaseSyncService caseMirror;
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
        String filter = keyword.isEmpty() ? "" : " AND (c.name LIKE ? OR c.id LIKE ?)";
        List<Object> args = new ArrayList<>();
        args.add(orgId);
        if (!keyword.isEmpty()) {
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM case_asset_catalog c WHERE c.organization_id = ? AND c.deleted = b'0'" + filter,
                Integer.class, args.toArray());
        args.add(pageSize);
        args.add((current - 1) * pageSize);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT c.id, c.name, c.source, c.hub_module_id hubModuleId, "
                        + "c.manually_renamed manuallyRenamed, c.create_time createTime, c.update_time updateTime, c.pinned, c.pin_time pinTime, "
                        + "COUNT(DISTINCT r.project_id) relatedProjectCount FROM case_asset_catalog c "
                        + "LEFT JOIN case_asset_catalog_project_rel r ON r.catalog_id = c.id "
                        + "WHERE c.organization_id = ? AND c.deleted = b'0'" + filter
                        + " GROUP BY c.id ORDER BY c.pinned DESC,c.pin_time DESC,c.update_time DESC,c.id LIMIT ? OFFSET ?", args.toArray());
        return Map.of("list", list, "total", total == null ? 0 : total, "current", current, "pageSize", pageSize);
    }

    public void pinCatalog(String catalogId, boolean pinned) {
        requireCatalog(catalogId);
        jdbcTemplate.update("UPDATE case_asset_catalog SET pinned=?,pin_time=? WHERE id=? AND organization_id=? AND deleted=b'0'",
                pinned, pinned ? System.currentTimeMillis() : null, catalogId, requireOrganization());
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
        List<Map<String,Object>> bindings = sourceBindings(request.getId());
        if (!bindings.isEmpty()) {
            var versions = jdbcTemplate.queryForList("SELECT update_time FROM functional_case WHERE id=? FOR UPDATE", Long.class, request.getId());
            if (request.getExpectedUpdateTime()==null || !request.getExpectedUpdateTime().equals(versions.getFirst())) throw new MSException("资产已更新，请刷新后重新编辑");
            if ("WRITE_BACK".equals(request.getSourceEditMode())) {
                Map<String,Object> selected = bindings.stream().filter(row -> Objects.equals(row.get("caseId"),request.getSourceCaseId())).findFirst().orElseThrow(() -> new MSException("请选择有效源用例"));
                String sourceId=(String)selected.get("caseId"),project=(String)selected.get("projectId");
                if(!SessionUtils.hasPermission(null,project,PermissionConstants.FUNCTIONAL_CASE_READ_UPDATE))throw new MSException("当前账号无权修改源用例，可选择脱离来源");
                var sourceVersions=jdbcTemplate.queryForList("SELECT update_time FROM functional_case WHERE id=? AND project_id=? AND deleted=0 FOR UPDATE",Long.class,sourceId,project);
                if(sourceVersions.isEmpty() || request.getExpectedSourceUpdateTime()==null || !request.getExpectedSourceUpdateTime().equals(sourceVersions.getFirst()))throw new MSException("源用例已更新，请刷新后比较内容再保存");
                FunctionalCase source=functionalCaseMapper.selectByPrimaryKey(sourceId);
                FunctionalCaseEditRequest write=new FunctionalCaseEditRequest();copySaveFields(request,write);
                write.setId(sourceId);write.setProjectId(project);write.setModuleId(source.getModuleId());write.setTemplateId(source.getTemplateId());write.setVersionId(source.getVersionId());
                write.setAttachments(null);write.setPublicCase(String.valueOf(source.getPublicCase()));
                var allowed=projectTemplateService.getTemplateDTOById(source.getTemplateId(),project,TemplateScene.FUNCTIONAL.name()).getCustomFields();
                if(request.getCustomFields()!=null && request.getCustomFields().stream().anyMatch(field -> allowed.stream().noneMatch(target -> target.getFieldId().equals(field.getFieldId()))))throw new MSException("资产与源用例模板字段不兼容，请在源用例修改或脱离来源");
                functionalCaseService.updateFunctionalCase(write,List.of(),SessionUtils.getUserId());
                auditSourceEdit(request.getId(),"WRITE_BACK",bindings);
                return functionalCaseMapper.selectByPrimaryKey(request.getId());
            }
            if(!"DETACH".equals(request.getSourceEditMode()))throw new MSException("该资产关联源用例，请明确选择回写源用例或脱离来源");
            auditSourceEdit(request.getId(),"DETACH",bindings);
            jdbcTemplate.update("DELETE FROM default_hub_case_map WHERE hub_case_id=?",request.getId());
            jdbcTemplate.update("DELETE FROM case_asset_source_relation WHERE asset_case_id=?",request.getId());
            for(var binding:bindings) jdbcTemplate.update("INSERT INTO functional_import_effect_outbox(id,effect_type,project_id,user_id,payload,next_retry_at,create_time,update_time) VALUES(?,'CASE_SYNC',?,?,?,?,?,?)",
                    IDGenerator.nextStr(),binding.get("projectId"),SessionUtils.getUserId(),io.metersphere.sdk.util.JSON.toJSONString(Map.of("caseId",binding.get("caseId"))),System.currentTimeMillis(),System.currentTimeMillis(),System.currentTimeMillis());
        }
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
        FunctionalCaseDetailDTO detail=functionalCaseService.getFunctionalCaseDetail(caseId, SessionUtils.getUserId(), true, true);
        detail.setSourceBindings(sourceBindings(caseId));
        return detail;
    }

    private List<Map<String,Object>> sourceBindings(String assetId) {
        return jdbcTemplate.queryForList("SELECT f.id caseId,f.project_id projectId,f.update_time updateTime FROM default_hub_case_map m JOIN functional_case f ON f.id=m.biz_case_id JOIN project p ON p.id=f.project_id AND p.organization_id=? AND p.deleted=0 WHERE m.hub_case_id=? AND f.deleted=0 AND f.latest=1 "
                + "UNION SELECT f.id caseId,f.project_id projectId,f.update_time updateTime FROM case_asset_source_relation r JOIN functional_case f ON f.project_id=r.source_project_id AND COALESCE(NULLIF(f.ref_id,''),f.id)=r.source_case_id JOIN project p ON p.id=f.project_id AND p.organization_id=? AND p.deleted=0 WHERE r.asset_case_id=? AND f.deleted=0 AND f.latest=1",requireOrganization(),assetId,requireOrganization(),assetId);
    }

    private void auditSourceEdit(String assetId,String mode,List<Map<String,Object>> bindings) {
        jdbcTemplate.update("INSERT INTO case_asset_source_edit_audit(id,asset_case_id,organization_id,operator_id,edit_mode,source_snapshot,create_time) VALUES(?,?,?,?,?,?,?)",
                IDGenerator.nextStr(),assetId,requireOrganization(),SessionUtils.getUserId(),mode,io.metersphere.sdk.util.JSON.toJSONString(bindings),System.currentTimeMillis());
    }

    public void uploadAttachment(String catalogId, String caseId, MultipartFile file) {
        requireAssetCase(caseId, catalogId, false);
        if(!sourceBindings(caseId).isEmpty())throw new MSException("请在源用例维护附件，或先保存脱离来源后操作");
        FunctionalCaseAssociationFileRequest request = new FunctionalCaseAssociationFileRequest();
        request.setCaseId(caseId);
        request.setProjectId(requireHubProjectId());
        functionalCaseAttachmentService.uploadOrAssociationFile(request, file, SessionUtils.getUserId());
    }

    public void deleteAttachment(String catalogId, String caseId, String fileId, Boolean local) {
        requireAssetCase(caseId, catalogId, false);
        if(!sourceBindings(caseId).isEmpty())throw new MSException("请在源用例维护附件，或先保存脱离来源后操作");
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

    public void batchEdit(String catalogId,String caseId,Long revision,String action,String priority,
                          String tagMode,List<String> tags,String sourceMode) {
        var detail=detail(catalogId,caseId);
        if(!Objects.equals(detail.getUpdateTime(),revision))throw new MSException("用例已更新，请刷新后重新提交批量操作");
        var request=new CaseAssetSaveRequest();
        request.setId(caseId);request.setCatalogId(catalogId);request.setExpectedUpdateTime(revision);
        request.setName(detail.getName());request.setPrerequisite(detail.getPrerequisite());
        request.setCaseEditType(detail.getCaseEditType());request.setSteps(detail.getSteps());
        request.setTextDescription(detail.getTextDescription());request.setExpectedResult(detail.getExpectedResult());
        request.setDescription(detail.getDescription());request.setTags(detail.getTags());
        // Read persisted values; do not replace unrelated custom fields with template defaults.
        List<CaseCustomFieldDTO> values=jdbcTemplate.query("SELECT field_id,value FROM functional_case_custom_field WHERE case_id=?",(rs,n)->{
            var value=new CaseCustomFieldDTO();value.setFieldId(rs.getString(1));value.setValue(rs.getString(2));return value;
        },caseId);
        if("PRIORITY".equals(action)) {
            List<String> priorityIds=jdbcTemplate.queryForList("SELECT id FROM custom_field WHERE name='functional_priority'",String.class);
            var field=detail.getCustomFields().stream().filter(f->priorityIds.contains(f.getFieldId())).findFirst()
                .orElseThrow(()->new MSException("该用例模板未配置可识别的用例等级字段，请单独编辑"));
            if(field.getOptions()==null || field.getOptions().stream().noneMatch(o->Objects.equals(o.getValue(),priority)))throw new MSException("所选用例等级与用例模板不匹配");
            values.removeIf(v->v.getFieldId().equals(field.getFieldId()));
            var v=new CaseCustomFieldDTO();v.setFieldId(field.getFieldId());v.setValue(priority);values.add(v);
        } else if("TAGS".equals(action)) {
            java.util.LinkedHashSet<String> result=new java.util.LinkedHashSet<>(detail.getTags()==null?List.of():detail.getTags());
            if("REPLACE".equals(tagMode))result.clear();
            if("REMOVE".equals(tagMode))result.removeAll(tags);else result.addAll(tags);
            if(result.size()>100)throw new MSException("修改后标签超过 100 个，请减少标签后重试");
            request.setTags(new ArrayList<>(result));
        } else throw new MSException("批量操作类型无效");
        request.setCustomFields(values);
        var bindings=detail.getSourceBindings();
        if(bindings!=null&&!bindings.isEmpty()) {
            request.setSourceEditMode(sourceMode);
            if("WRITE_BACK".equals(sourceMode)) {
                if(bindings.size()!=1)throw new MSException("该资产存在多个源用例，请在详情中选择具体来源后编辑");
                request.setSourceCaseId((String)bindings.getFirst().get("caseId"));
                request.setExpectedSourceUpdateTime(((Number)bindings.getFirst().get("updateTime")).longValue());
            }
        }
        updateCase(request);
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

    @Transactional(readOnly=true)
    public List<Map<String,Object>> syncFailures(String catalogId) {
        requireCatalog(catalogId);
        return jdbcTemplate.queryForList("SELECT e.id,e.state,e.attempts,e.error_message message,e.update_time updateTime,JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.caseId')) caseId "
                + "FROM functional_import_effect_outbox e JOIN case_asset_catalog_project_rel r ON r.project_id=e.project_id "
                + "WHERE r.catalog_id=? AND e.effect_type='CASE_SYNC' AND e.state IN ('PENDING','FAILED') AND e.attempts>0 ORDER BY e.update_time DESC LIMIT 100",catalogId);
    }
    public void retrySyncFailure(String catalogId,String effectId) {
        requireCatalog(catalogId);
        int changed=jdbcTemplate.update("UPDATE functional_import_effect_outbox e JOIN case_asset_catalog_project_rel r ON r.project_id=e.project_id SET e.state='PENDING',e.attempts=0,e.error_message=NULL,e.error_code=NULL,e.next_retry_at=?,e.update_time=? "
                + "WHERE r.catalog_id=? AND e.id=? AND e.effect_type='CASE_SYNC' AND e.state='FAILED'",System.currentTimeMillis(),System.currentTimeMillis(),catalogId,effectId);
        if(changed!=1)throw new MSException("同步任务状态已变化，请刷新后查看");
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

    /**
     * Creates the project catalog in its own committed transaction before case-level synchronization starts.
     * Successful cases can then stay committed when a different historical case contains incompatible data.
     */
    public Map<String, Object> prepareHistoricalProject(String projectId, String organizationId, String operator) {
        Project sourceProject = projectMapper.selectByPrimaryKey(projectId);
        if (sourceProject == null || Boolean.TRUE.equals(sourceProject.getDeleted())
                || !StringUtils.equals(sourceProject.getOrganizationId(), organizationId)
                || defaultHubProjectService.isDefaultProject(projectId)) {
            return Map.of("skipped", true, "caseIds", List.of());
        }
        Map<String, Object> catalog = upsertCatalog(organizationId, sourceProject.getName(), "PROJECT", projectId, operator);
        List<String> caseIds = listHistoricalSources(projectId).stream()
                .map(source -> String.valueOf(source.get("id"))).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skipped", false);
        result.put("hubModuleId", String.valueOf(catalog.get("hubModuleId")));
        result.put("caseIds", caseIds);
        return result;
    }

    public String syncHistoricalCase(String projectId, String organizationId, String operator,
                                     String hubModuleId, String sourceCaseId) {
        Project sourceProject = projectMapper.selectByPrimaryKey(projectId);
        if (sourceProject == null || Boolean.TRUE.equals(sourceProject.getDeleted())
                || !StringUtils.equals(sourceProject.getOrganizationId(), organizationId)) {
            throw new MSException("历史项目不存在、已删除或不属于当前组织");
        }
        List<Map<String, Object>> sources = listHistoricalSources(projectId, sourceCaseId);
        if (sources.isEmpty()) {
            return "SKIPPED";
        }
        return syncHistoricalCaseSource(sourceProject, hubModuleId, organizationId, operator, sources.getFirst());
    }

    private Map<String, Integer> syncHistoricalCases(Project sourceProject, Map<String, Object> catalog,
                                                     String organizationId, String operator) {
        String hubProjectId = requireHubProjectId();
        if (StringUtils.equals(sourceProject.getId(), hubProjectId)) return Map.of("created", 0, "updated", 0, "skipped", 0);
        List<Map<String, Object>> sources = listHistoricalSources(sourceProject.getId());
        TemplateDTO template = projectTemplateService.getDefaultTemplateDTO(hubProjectId, TemplateScene.FUNCTIONAL.name());
        int created = 0;
        int updated = 0;
        for (Map<String, Object> source : sources) {
            String outcome = syncHistoricalCaseSource(sourceProject, String.valueOf(catalog.get("hubModuleId")), organizationId,
                    operator, source, template);
            if (StringUtils.equals(outcome, "UPDATED")) updated++; else created++;
        }
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM functional_case WHERE project_id=? AND deleted=b'0' AND latest=b'1'",
                Integer.class, sourceProject.getId());
        return Map.of("created", created, "updated", updated,
                "skipped", Math.max(0, (total == null ? 0 : total) - created - updated));
    }

    private List<Map<String, Object>> listHistoricalSources(String projectId) {
        return listHistoricalSources(projectId, null);
    }

    private List<Map<String, Object>> listHistoricalSources(String projectId, String sourceCaseId) {
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        String caseFilter = StringUtils.isBlank(sourceCaseId) ? "" : " AND fc.id=? ";
        if (StringUtils.isNotBlank(sourceCaseId)) args.add(sourceCaseId);
        return jdbcTemplate.queryForList("SELECT fc.id,COALESCE(NULLIF(fc.ref_id,''),fc.id) sourceRefId,"
                + "fc.name,fc.case_edit_type caseEditType,"
                + "fc.tags,fc.update_time updateTime,rel.asset_case_id assetCaseId,rel.source_update_time sourceSyncedTime "
                + "FROM functional_case fc LEFT JOIN case_asset_source_relation rel "
                + "ON rel.source_project_id=fc.project_id "
                + "AND rel.source_case_id=COALESCE(NULLIF(fc.ref_id,''),fc.id) "
                + "WHERE fc.project_id=? AND fc.deleted=b'0' AND fc.latest=b'1' " + caseFilter
                + "AND (rel.id IS NULL OR COALESCE(fc.update_time,0)>COALESCE(rel.source_update_time,0)) "
                + "ORDER BY fc.create_time,fc.id", args.toArray());
    }

    private String syncHistoricalCaseSource(Project sourceProject, String hubModuleId, String organizationId,
                                            String operator, Map<String, Object> source) {
        TemplateDTO template = projectTemplateService.getDefaultTemplateDTO(
                requireHubProjectId(), TemplateScene.FUNCTIONAL.name());
        return syncHistoricalCaseSource(sourceProject, hubModuleId, organizationId, operator, source, template);
    }

    private String syncHistoricalCaseSource(Project sourceProject, String hubModuleId, String organizationId,
                                            String operator, Map<String, Object> source, TemplateDTO template) {
        String sourceId=String.valueOf(source.get("id"));
        Integer before=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM default_hub_case_map WHERE biz_case_id=?",Integer.class,sourceId);
        boolean existed=source.get("assetCaseId")!=null || (before!=null && before>0);
        caseMirror.syncCaseUpsert(sourceProject.getId(),sourceId,operator);
        return existed?"UPDATED":"CREATED";
    }


    @Transactional(readOnly = true)
    public Map<String, Object> getHistorySyncJob(String jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id jobId,status,total_count total,success_count success," +
                        "skipped_count skipped,failed_count failed,case_created_count caseCreated,case_updated_count caseUpdated," +
                        "case_skipped_count caseSkipped,create_time createTime,update_time updateTime,finish_time finishTime " +
                        "FROM case_asset_history_sync_job WHERE id=? AND organization_id=?", jobId, requireOrganization());
        if (rows.isEmpty()) throw new MSException("历史用例同步任务不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put("items", jdbcTemplate.queryForList("SELECT i.project_id projectId,p.name projectName,i.status," +
                "i.case_created_count caseCreated,i.case_updated_count caseUpdated," +
                "i.case_skipped_count caseSkipped,i.failure_reason failureReason " +
                "FROM case_asset_history_sync_item i LEFT JOIN project p ON p.id=i.project_id " +
                "WHERE i.job_id=? ORDER BY i.create_time,i.project_id", jobId));
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

    static String normalizeCaseEditType(String value) {
        return StringUtils.equalsIgnoreCase(StringUtils.trim(value), "TEXT") ? "TEXT" : "STEP";
    }

    private Map<String, Object> upsertCatalog(String orgId, String rawName, String source, String projectId, String operator) {
        // Serialize name allocation and module/catalog mapping in the same transaction.
        jdbcTemplate.queryForList("SELECT id FROM organization WHERE id=? FOR UPDATE", orgId);
        if (StringUtils.isNotBlank(projectId)) {
            List<Map<String, Object>> linked = jdbcTemplate.queryForList("SELECT c.id,c.name,c.hub_module_id hubModuleId,c.source,c.manually_renamed manuallyRenamed,(SELECT COUNT(*) FROM case_asset_catalog_project_rel cr WHERE cr.catalog_id=c.id) relatedCount FROM case_asset_catalog c "
                    + "JOIN case_asset_catalog_project_rel r ON r.catalog_id=c.id WHERE r.project_id=? AND c.organization_id=? AND c.deleted=b'0'", projectId, orgId);
            if (!linked.isEmpty()) {
                var catalog=linked.getFirst();
                boolean manual=Boolean.TRUE.equals(catalog.get("manuallyRenamed")) || (catalog.get("manuallyRenamed") instanceof Number n && n.intValue()!=0);
                if("PROJECT".equals(catalog.get("source"))&&!manual&&((Number)catalog.get("relatedCount")).longValue()==1) {
                    String next=cleanName(rawName),key=normalize(next),id=(String)catalog.get("id");
                    Long conflicts=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM case_asset_catalog WHERE organization_id=? AND normalized_name=? AND deleted=0 AND id<>?",Long.class,orgId,key,id);
                    if(conflicts!=null&&conflicts>0){next=cleanName(StringUtils.left(next,220)+" ["+StringUtils.right(projectId,12)+"]");key=normalize(next);}
                    if(!next.equals(catalog.get("name"))){
                        jdbcTemplate.update("UPDATE case_asset_catalog SET name=?,normalized_name=?,update_user=?,update_time=? WHERE id=?",next,key,operator,System.currentTimeMillis(),id);
                        catalog.put("name",next);
                    }
                }
                return catalog;
            }
        }
        String name = cleanName(rawName);
        String normalized = normalize(name);
        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT id,name,hub_module_id hubModuleId FROM case_asset_catalog "
                + "WHERE organization_id=? AND normalized_name=? AND deleted=b'0'", orgId, normalized);
        if (!existing.isEmpty() && StringUtils.isBlank(projectId)) return existing.getFirst();
        if (!existing.isEmpty()) {
            name = cleanName(StringUtils.left(name, 220) + " [" + StringUtils.right(projectId, 12) + "]");
            normalized = normalize(name);
            existing = jdbcTemplate.queryForList("SELECT id FROM case_asset_catalog WHERE organization_id=? AND normalized_name=? AND deleted=b'0'", orgId, normalized);
            if (!existing.isEmpty()) throw new MSException("项目资产目录名称冲突，请修改项目名称后重试");
        }
        String hubProjectId = requireHubProjectId();
        List<Map<String, Object>> trusted = StringUtils.isBlank(projectId) ? List.of() : jdbcTemplate.queryForList(
                "SELECT id FROM functional_case_module WHERE project_id=? AND ref_project_id=? AND module_type=? AND parent_id=?",
                hubProjectId, projectId, DefaultHubConstants.MODULE_TYPE_FOLDER, ModuleConstants.ROOT_NODE_PARENT_ID);
        if (trusted.size() > 1) throw new MSException("该项目存在多个历史资产目录，请先核对目录来源后重试");
        long now = System.currentTimeMillis();
        String moduleId;
        if (trusted.isEmpty()) {
            Integer conflicts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM functional_case_module WHERE project_id=? AND parent_id=? AND name=?", Integer.class,
                    hubProjectId, ModuleConstants.ROOT_NODE_PARENT_ID, name);
            if (conflicts != null && conflicts > 0) {
                if (StringUtils.isBlank(projectId)) throw new MSException("同名历史目录尚未关联资产，请先核对目录来源");
                name = cleanName(StringUtils.left(name, 220) + " [" + StringUtils.right(projectId, 12) + "]");
                normalized = normalize(name);
            }
            moduleId = IDGenerator.nextStr();
            jdbcTemplate.update("INSERT INTO functional_case_module(id,project_id,name,parent_id,module_type,ref_project_id,pos,create_time,update_time,create_user,update_user) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    moduleId,hubProjectId,name,ModuleConstants.ROOT_NODE_PARENT_ID,DefaultHubConstants.MODULE_TYPE_FOLDER,projectId,now,now,now,operator,operator);
        } else moduleId = String.valueOf(trusted.getFirst().get("id"));
        String catalogId = IDGenerator.nextStr();
        jdbcTemplate.update("INSERT INTO case_asset_catalog(id,organization_id,name,normalized_name,hub_module_id,source,manually_renamed,deleted,create_user,update_user,create_time,update_time) VALUES(?,?,?,?,?,?,b'0',b'0',?,?,?,?)",
                catalogId,orgId,name,normalized,moduleId,source,operator,operator,now,now);
        if (StringUtils.isNotBlank(projectId)) jdbcTemplate.update("INSERT INTO case_asset_catalog_project_rel(id,catalog_id,project_id,relation_type,create_user,create_time) VALUES(?,?,?,'PROJECT_ID',?,?)",
                IDGenerator.nextStr(),catalogId,projectId,operator,now);
        return Map.of("id",catalogId,"name",name,"hubModuleId",moduleId);
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
        List<Map<String, Object>> refs = jdbcTemplate.queryForList("SELECT DISTINCT l.source_case_id sourceCaseId, p.id, p.name"
                + REFERENCED_PROJECTS_FROM_SQL
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
        String base = " FROM (SELECT DISTINCT p.id, p.name" + REFERENCED_PROJECTS_FROM_SQL
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
