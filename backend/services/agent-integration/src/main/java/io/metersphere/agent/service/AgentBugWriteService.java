package io.metersphere.agent.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.agent.constants.AgentAttachmentPurpose;
import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.constants.AgentErrorCode;
import io.metersphere.agent.dto.AgentBugCreateRequest;
import io.metersphere.agent.dto.AgentBugDTO;
import io.metersphere.agent.dto.AgentBugRelateCaseRequest;
import io.metersphere.agent.dto.AgentBugSearchRequest;
import io.metersphere.agent.dto.AgentBugSearchResponse;
import io.metersphere.agent.dto.AgentBugUpdateRequest;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.dto.request.BugEditRequest;
import io.metersphere.bug.dto.request.BugPageRequest;
import io.metersphere.bug.dto.response.BugCustomFieldDTO;
import io.metersphere.bug.dto.response.BugDTO;
import io.metersphere.bug.dto.response.BugDetailDTO;
import io.metersphere.bug.enums.BugAttachmentSourceType;
import io.metersphere.bug.mapper.BugMapper;
import io.metersphere.bug.service.BugAttachmentService;
import io.metersphere.bug.service.BugHistoryService;
import io.metersphere.bug.service.BugRelateCaseCommonService;
import io.metersphere.bug.service.BugService;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.project.service.ProjectTemplateService;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.request.AssociateOtherCaseRequest;
import io.metersphere.sdk.constants.CaseType;
import io.metersphere.sdk.constants.TemplateScene;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.AgentTempAttachment;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.dto.OperationHistoryDTO;
import io.metersphere.system.dto.request.OperationHistoryRequest;
import io.metersphere.system.dto.sdk.TemplateCustomFieldDTO;
import io.metersphere.system.dto.sdk.TemplateDTO;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.PageUtils;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentBugWriteService {
    @Resource
    private BugService bugService;
    @Resource
    private BugMapper bugMapper;
    @Resource
    private BugRelateCaseCommonService bugRelateCaseCommonService;
    @Resource
    private BugAttachmentService bugAttachmentService;
    @Resource
    private BugHistoryService bugHistoryService;
    @Resource
    private ProjectTemplateService projectTemplateService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private AgentProjectService agentProjectService;
    @Resource
    private AgentTempAttachmentService agentTempAttachmentService;
    @Resource
    private AgentExecLogService agentExecLogService;
    @Resource
    private TestAssetVersionService testAssetVersionService;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;

    public AgentBugSearchResponse search(AgentBugSearchRequest request) {
        String projectId = resolveAndAssertProject(request.getProjectId());
        BugPageRequest pageRequest = new BugPageRequest();
        pageRequest.setProjectId(projectId);
        pageRequest.setUseTrash(false);
        pageRequest.setCurrent(Math.max(request.getCurrent(), 1));
        pageRequest.setPageSize(AgentConstants.normalizePageSize(request.getPageSize()));
        if (StringUtils.isNotBlank(request.getQuery())) {
            pageRequest.initKeyword(request.getQuery().trim());
        }
        Map<String, List<String>> filter = new HashMap<>();
        if (CollectionUtils.isNotEmpty(request.getStatus())) {
            filter.put("status", request.getStatus());
        }
        if (CollectionUtils.isNotEmpty(request.getHandleUserIds())) {
            filter.put("handleUser", request.getHandleUserIds());
        }
        if (!filter.isEmpty()) {
            pageRequest.setFilter(filter);
        }

        Page<Object> page = PageHelper.startPage(pageRequest.getCurrent(), pageRequest.getPageSize(), "pos desc");
        List<BugDTO> bugs = bugService.list(pageRequest);
        var pager = PageUtils.setPageInfo(page, bugs);

        AgentBugSearchResponse response = new AgentBugSearchResponse();
        response.setTotal(pager.getTotal());
        response.setBugs(bugs.stream().map(this::toListDto).toList());
        return response;
    }

    public AgentBugDTO get(String bugId) {
        String userId = requireUserId();
        BugDetailDTO detail = bugService.get(bugId, userId, "zh_CN");
        assertProjectAllowed(detail.getProjectId());
        return toDetailDto(detail);
    }

    public Object getTemplate(String projectId, String templateId) {
        String resolvedProjectId = resolveAndAssertProject(projectId);
        TemplateDTO templateDTO = resolveTemplate(resolvedProjectId, templateId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", templateDTO.getId());
        view.put("name", templateDTO.getName());
        view.put("platformDefault", templateDTO.getPlatformDefault());
        List<Map<String, Object>> fields = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(templateDTO.getCustomFields())) {
            for (TemplateCustomFieldDTO field : templateDTO.getCustomFields()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fieldId", field.getFieldId());
                item.put("fieldName", field.getFieldName());
                item.put("type", field.getType());
                item.put("required", field.getRequired());
                item.put("defaultValue", field.getDefaultValue());
                item.put("options", field.getOptions());
                item.put("internalFieldKey", field.getInternalFieldKey());
                item.put("fieldKey", field.getFieldKey());
                fields.add(item);
            }
        }
        view.put("customFields", fields);
        return view;
    }

    public Object listHistory(String projectId, String bugId, Integer current, Integer pageSize) {
        Bug bug = requireBugInProject(projectId, bugId);
        OperationHistoryRequest request = new OperationHistoryRequest();
        request.setProjectId(bug.getProjectId());
        request.setSourceId(bug.getId());
        request.setCurrent(current == null || current < 1 ? 1 : current);
        request.setPageSize(AgentConstants.normalizePageSize(pageSize));
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize(), "id desc");
        List<OperationHistoryDTO> list = bugHistoryService.list(request);
        Pager<List<OperationHistoryDTO>> pager = PageUtils.setPageInfo(page, list);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pager.getTotal());
        result.put("list", list);
        return result;
    }

    public AgentBugDTO create(AgentBugCreateRequest request) {
        String userId = requireUserId();
        String projectId = resolveAndAssertProject(request.getProjectId());
        request.setProjectId(projectId);
        Project project = requireProject(projectId);
        TemplateDTO templateDTO = resolveTemplate(projectId, request.getTemplateId());
        String templateId = templateDTO.getId();

        BugEditRequest editRequest = new BugEditRequest();
        editRequest.setId(IDGenerator.nextStr());
        editRequest.setProjectId(projectId);
        editRequest.setTitle(request.getTitle());
        editRequest.setDescription(StringUtils.defaultString(request.getDescription()));
        editRequest.setTemplateId(templateId);
        editRequest.setTags(request.getTags());
        editRequest.setCustomFields(buildCustomFields(templateDTO, request.getCustomFields(), userId));
        if (StringUtils.isNotBlank(request.getCaseId())) {
            editRequest.setCaseId(request.getCaseId());
            editRequest.setCaseType(StringUtils.defaultIfBlank(request.getCaseType(), CaseType.FUNCTIONAL_CASE.getKey()));
            editRequest.setTestPlanId(request.getTestPlanId());
            editRequest.setTestPlanCaseId(request.getTestPlanCaseId());
        }

        Bug bug = bugService.addOrUpdate(editRequest, new ArrayList<>(), userId, project.getOrganizationId(), false);
        attachTempFiles(bug.getId(), projectId, request.getAttachmentIds());
        relateAdditionalCases(projectId, bug.getId(), request.getAddCaseIds(), request.getCaseType());
        publishBugRelations(bug, request, userId);
        agentExecLogService.audit("BUG_CREATE", bug.getId(), JSON.toJSONString(request));

        AgentBugDTO dto = new AgentBugDTO();
        dto.setId(bug.getId());
        dto.setNum(bug.getNum());
        dto.setTitle(bug.getTitle());
        dto.setProjectId(bug.getProjectId());
        dto.setStatus(bug.getStatus());
        dto.setCaseId(request.getCaseId());
        return dto;
    }

    void publishBugRelations(Bug bug, AgentBugCreateRequest request, String userId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assetType", "BUG");
        snapshot.put("assetId", bug.getId());
        snapshot.put("name", bug.getTitle());
        snapshot.put("bugNumber", bug.getNum());
        snapshot.put("status", bug.getStatus());
        snapshot.put("description", StringUtils.defaultString(request.getDescription()));
        snapshot.put("handleUser", bug.getHandleUser());
        snapshot.put("tags", bug.getTags());
        Long sourceTime = bug.getUpdateTime() != null ? bug.getUpdateTime() : bug.getCreateTime();
        var bugVersion = testAssetVersionService.publish(bug.getProjectId(), "BUG", bug.getId(),
                sourceTime == null ? null : String.valueOf(sourceTime),
                JSON.toJSONString(snapshot), userId);
        List<String> caseIds = new ArrayList<>();
        if (StringUtils.isNotBlank(request.getCaseId())) caseIds.add(request.getCaseId());
        if (CollectionUtils.isNotEmpty(request.getAddCaseIds())) caseIds.addAll(request.getAddCaseIds());
        caseIds.stream().filter(StringUtils::isNotBlank).distinct().forEach(caseId -> {
            FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(caseId);
            if (functionalCase == null || !StringUtils.equals(bug.getProjectId(), functionalCase.getProjectId())) return;
            String stableCaseId = StringUtils.defaultIfBlank(functionalCase.getRefId(), functionalCase.getId());
            var caseVersion = testAssetVersionService.publish(bug.getProjectId(), "CASE", stableCaseId,
                    functionalCase.getVersionId(), JSON.toJSONString(functionalCase), userId);
            testAssetVersionService.relate(bug.getProjectId(), "REPORTS", "CASE", stableCaseId,
                    caseVersion.getId(), "BUG", bug.getId(), bugVersion.getId(),
                    JSON.toJSONString(Map.of("source", "AGENT_BUG_CREATE")), userId);
        });
    }

    public AgentBugDTO update(AgentBugUpdateRequest request) {
        String userId = requireUserId();
        String projectId = resolveAndAssertProject(request.getProjectId());
        request.setProjectId(projectId);
        Project project = requireProject(projectId);
        BugDetailDTO existing = bugService.get(request.getBugId(), userId, "zh_CN");
        if (!StringUtils.equals(existing.getProjectId(), projectId)) {
            throw new MSException(AgentErrorCode.RESOURCE_PROJECT_MISMATCH, "缺陷不属于指定项目");
        }
        Bug existingBug = bugMapper.selectByPrimaryKey(request.getBugId());
        if (existingBug == null) {
            throw new MSException(AgentErrorCode.RESOURCE_NOT_FOUND, "缺陷不存在: " + request.getBugId());
        }
        if (request.getExpectedUpdateTime() != null && existingBug.getUpdateTime() != null
                && !request.getExpectedUpdateTime().equals(existingBug.getUpdateTime())) {
            throw new MSException(AgentErrorCode.VERSION_CONFLICT,
                    "缺陷已被他人更新，当前 updateTime=" + existingBug.getUpdateTime());
        }

        String templateId = StringUtils.defaultIfBlank(request.getTemplateId(), existing.getTemplateId());
        TemplateDTO templateDTO = resolveTemplate(projectId, templateId);

        BugEditRequest editRequest = new BugEditRequest();
        editRequest.setId(existing.getId());
        editRequest.setProjectId(projectId);
        editRequest.setTemplateId(templateId);
        editRequest.setTitle(StringUtils.defaultIfBlank(request.getTitle(), existing.getTitle()));
        editRequest.setDescription(request.getDescription() != null
                ? request.getDescription()
                : StringUtils.defaultString(existing.getDescription()));
        editRequest.setTags(request.getTags() != null ? request.getTags() : existing.getTags());

        Map<String, String> mergedCustom = new LinkedHashMap<>();
        if (CollectionUtils.isNotEmpty(existing.getCustomFields())) {
            for (BugCustomFieldDTO field : existing.getCustomFields()) {
                if (field != null && StringUtils.isNotBlank(field.getId()) && field.getValue() != null) {
                    mergedCustom.put(field.getId(), String.valueOf(field.getValue()));
                }
            }
        }
        if (request.getCustomFields() != null) {
            mergedCustom.putAll(request.getCustomFields());
        }
        editRequest.setCustomFields(buildCustomFields(templateDTO, mergedCustom, userId));

        Bug bug = bugService.addOrUpdate(editRequest, new ArrayList<>(), userId, project.getOrganizationId(), true);
        attachTempFiles(bug.getId(), projectId, request.getAttachmentIds());
        relateAdditionalCases(projectId, bug.getId(), request.getAddCaseIds(), null);
        if (CollectionUtils.isNotEmpty(request.getRemoveRelationIds())) {
            for (String relationId : request.getRemoveRelationIds()) {
                bugRelateCaseCommonService.unRelate(relationId);
            }
        }
        agentExecLogService.audit("BUG_UPDATE", bug.getId(), JSON.toJSONString(request));
        return get(bug.getId());
    }

    public void relateCase(AgentBugRelateCaseRequest request) {
        String userId = requireUserId();
        String projectId = resolveAndAssertProject(request.getProjectId());
        requireBugInProject(projectId, request.getBugId());
        AssociateOtherCaseRequest associate = new AssociateOtherCaseRequest();
        associate.setProjectId(projectId);
        associate.setSourceId(request.getBugId());
        associate.setSourceType(StringUtils.defaultIfBlank(request.getCaseType(), CaseType.FUNCTIONAL_CASE.getKey()));
        associate.setSelectIds(request.getCaseIds());
        associate.setSelectAll(false);
        bugRelateCaseCommonService.relateCase(associate, false, userId);
        agentExecLogService.audit("BUG_RELATE_CASE", request.getBugId(), JSON.toJSONString(request));
    }

    public void unrelateCase(String relationId) {
        if (StringUtils.isBlank(relationId)) {
            throw new MSException(AgentErrorCode.RESOURCE_NOT_FOUND, "relationId 不能为空");
        }
        bugRelateCaseCommonService.unRelate(relationId);
        agentExecLogService.audit("BUG_UNRELATE_CASE", relationId, relationId);
    }

    private void attachTempFiles(String bugId, String projectId, List<String> attachmentIds) {
        if (CollectionUtils.isEmpty(attachmentIds)) {
            return;
        }
        List<AgentTempAttachment> temps = agentTempAttachmentService.requireUsable(
                attachmentIds, projectId, AgentAttachmentPurpose.BUG_DETAIL);
        List<String> fileIds = agentTempAttachmentService.toFileIds(temps);
        bugAttachmentService.transferTmpFile(
                bugId, projectId, fileIds, SessionUtils.getUserId(), BugAttachmentSourceType.ATTACHMENT.name());
        agentTempAttachmentService.markLinked(temps);
    }

    private void relateAdditionalCases(String projectId, String bugId, List<String> addCaseIds, String caseType) {
        if (CollectionUtils.isEmpty(addCaseIds)) {
            return;
        }
        AgentBugRelateCaseRequest relateRequest = new AgentBugRelateCaseRequest();
        relateRequest.setProjectId(projectId);
        relateRequest.setBugId(bugId);
        relateRequest.setCaseIds(addCaseIds);
        relateRequest.setCaseType(StringUtils.defaultIfBlank(caseType, CaseType.FUNCTIONAL_CASE.getKey()));
        relateCase(relateRequest);
    }

    private Bug requireBugInProject(String projectId, String bugId) {
        String resolvedProjectId = resolveAndAssertProject(projectId);
        Bug bug = bugMapper.selectByPrimaryKey(bugId);
        if (bug == null || Boolean.TRUE.equals(bug.getDeleted())) {
            throw new MSException(AgentErrorCode.RESOURCE_NOT_FOUND, "缺陷不存在: " + bugId);
        }
        if (!StringUtils.equals(bug.getProjectId(), resolvedProjectId)) {
            throw new MSException(AgentErrorCode.RESOURCE_PROJECT_MISMATCH, "缺陷不属于指定项目");
        }
        return bug;
    }

    private String resolveAndAssertProject(String projectId) {
        String resolved = agentProjectService.resolveProjectId(projectId);
        assertProjectAllowed(resolved);
        return resolved;
    }

    private void assertProjectAllowed(String projectId) {
        AgentToken token = AgentTokenContext.get();
        if (token != null && !AgentTokenProjectAccess.allows(token, projectId)) {
            throw new MSException(AgentErrorCode.PROJECT_NOT_ALLOWED, "Token 无权访问项目: " + projectId);
        }
    }

    private AgentBugDTO toListDto(BugDTO bug) {
        AgentBugDTO dto = new AgentBugDTO();
        dto.setId(bug.getId());
        dto.setNum(bug.getNum());
        dto.setTitle(bug.getTitle());
        dto.setProjectId(bug.getProjectId());
        dto.setStatus(bug.getStatus());
        dto.setStatusName(bug.getStatusName());
        dto.setHandleUser(bug.getHandleUser());
        dto.setHandleUserName(bug.getHandleUserName());
        dto.setCreateUser(bug.getCreateUser());
        dto.setCreateUserName(bug.getCreateUserName());
        dto.setCreateTime(bug.getCreateTime());
        dto.setUpdateTime(bug.getUpdateTime());
        dto.setDescription(bug.getDescription());
        dto.setTemplateId(bug.getTemplateId());
        dto.setTags(bug.getTags());
        dto.setRelationCaseCount(bug.getRelationCaseCount());
        return dto;
    }

    private AgentBugDTO toDetailDto(BugDetailDTO detail) {
        AgentBugDTO dto = new AgentBugDTO();
        dto.setId(detail.getId());
        dto.setNum(detail.getNum());
        dto.setTitle(detail.getTitle());
        dto.setProjectId(detail.getProjectId());
        dto.setStatus(detail.getStatus());
        dto.setHandleUser(detail.getHandleUser());
        dto.setHandleUserName(detail.getHandleUserName());
        dto.setCreateUser(detail.getCreateUser());
        dto.setCreateUserName(detail.getCreateUserName());
        dto.setCreateTime(detail.getCreateTime());
        dto.setDescription(detail.getDescription());
        dto.setTemplateId(detail.getTemplateId());
        dto.setTags(detail.getTags());
        dto.setRelationCaseCount((int) detail.getLinkCaseCount());
        if (CollectionUtils.isNotEmpty(detail.getCustomFields())) {
            Map<String, String> fields = new LinkedHashMap<>();
            for (BugCustomFieldDTO field : detail.getCustomFields()) {
                if (field != null && StringUtils.isNotBlank(field.getId()) && field.getValue() != null) {
                    fields.put(field.getId(), String.valueOf(field.getValue()));
                }
            }
            dto.setCustomFields(fields);
        }
        return dto;
    }

    private Project requireProject(String projectId) {
        Project project = projectMapper.selectByPrimaryKey(projectId);
        if (project == null) {
            throw new MSException(AgentErrorCode.PROJECT_NOT_FOUND, "项目不存在: " + projectId);
        }
        return project;
    }

    private TemplateDTO resolveTemplate(String projectId, String templateId) {
        String scene = TemplateScene.BUG.name();
        TemplateDTO templateDTO;
        if (StringUtils.isNotBlank(templateId)) {
            templateDTO = projectTemplateService.getTemplateDTOById(templateId, projectId, scene);
        } else {
            templateDTO = projectTemplateService.getDefaultTemplateDTO(projectId, scene);
            if (templateDTO == null || StringUtils.isBlank(templateDTO.getId())) {
                throw new MSException(AgentErrorCode.RESOURCE_NOT_FOUND, "项目未配置缺陷默认模板");
            }
        }
        return templateDTO;
    }

    private List<BugCustomFieldDTO> buildCustomFields(TemplateDTO templateDTO, Map<String, String> customFields, String userId) {
        List<BugCustomFieldDTO> result = new ArrayList<>();
        if (templateDTO == null || CollectionUtils.isEmpty(templateDTO.getCustomFields())) {
            return result;
        }
        for (TemplateCustomFieldDTO field : templateDTO.getCustomFields()) {
            BugCustomFieldDTO dto = new BugCustomFieldDTO();
            dto.setId(field.getFieldId());
            dto.setName(field.getFieldName());
            dto.setType(field.getType());
            if (customFields != null && customFields.containsKey(field.getFieldId())) {
                dto.setValue(customFields.get(field.getFieldId()));
            } else if (field.getDefaultValue() != null) {
                String defaultValue = String.valueOf(field.getDefaultValue());
                if (StringUtils.contains(defaultValue, "CREATE_USER")) {
                    dto.setValue(userId);
                } else {
                    dto.setValue(defaultValue);
                }
            } else if (Boolean.TRUE.equals(field.getRequired())) {
                throw new MSException("缺陷必填自定义字段缺失: " + field.getFieldName() + " (" + field.getFieldId() + ")");
            } else {
                continue;
            }
            result.add(dto);
        }
        return result;
    }

    private String requireUserId() {
        String userId = SessionUtils.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new MSException("无法解析 Agent Token 对应用户");
        }
        return userId;
    }
}
