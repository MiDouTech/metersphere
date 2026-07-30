package io.metersphere.agent.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.agent.dto.AgentBugCreateRequest;
import io.metersphere.agent.dto.AgentBugDTO;
import io.metersphere.agent.dto.AgentBugRelateCaseRequest;
import io.metersphere.agent.dto.AgentBugSearchRequest;
import io.metersphere.agent.dto.AgentBugSearchResponse;
import io.metersphere.agent.dto.AgentBugUpdateRequest;
import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.dto.request.BugEditRequest;
import io.metersphere.bug.dto.request.BugPageRequest;
import io.metersphere.bug.dto.response.BugCustomFieldDTO;
import io.metersphere.bug.dto.response.BugDTO;
import io.metersphere.bug.dto.response.BugDetailDTO;
import io.metersphere.bug.service.BugRelateCaseCommonService;
import io.metersphere.bug.service.BugService;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.project.service.ProjectTemplateService;
import io.metersphere.request.AssociateOtherCaseRequest;
import io.metersphere.sdk.constants.CaseType;
import io.metersphere.sdk.constants.TemplateScene;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.sdk.TemplateCustomFieldDTO;
import io.metersphere.system.dto.sdk.TemplateDTO;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.PageUtils;
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
    private BugRelateCaseCommonService bugRelateCaseCommonService;
    @Resource
    private ProjectTemplateService projectTemplateService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private AgentExecLogService agentExecLogService;

    public AgentBugSearchResponse search(AgentBugSearchRequest request) {
        BugPageRequest pageRequest = new BugPageRequest();
        pageRequest.setProjectId(request.getProjectId());
        pageRequest.setUseTrash(false);
        pageRequest.setCurrent(Math.max(request.getCurrent(), 1));
        pageRequest.setPageSize(Math.min(Math.max(request.getPageSize(), 5), 500));
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
        return toDetailDto(detail);
    }

    public AgentBugDTO create(AgentBugCreateRequest request) {
        String userId = requireUserId();
        Project project = requireProject(request.getProjectId());
        TemplateDTO templateDTO = resolveTemplate(request.getProjectId(), request.getTemplateId());
        String templateId = templateDTO.getId();

        BugEditRequest editRequest = new BugEditRequest();
        editRequest.setId(IDGenerator.nextStr());
        editRequest.setProjectId(request.getProjectId());
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

    public AgentBugDTO update(AgentBugUpdateRequest request) {
        String userId = requireUserId();
        Project project = requireProject(request.getProjectId());
        BugDetailDTO existing = bugService.get(request.getBugId(), userId, "zh_CN");
        if (!StringUtils.equals(existing.getProjectId(), request.getProjectId())) {
            throw new MSException("缺陷不属于指定项目");
        }

        String templateId = StringUtils.defaultIfBlank(request.getTemplateId(), existing.getTemplateId());
        TemplateDTO templateDTO = resolveTemplate(request.getProjectId(), templateId);

        BugEditRequest editRequest = new BugEditRequest();
        editRequest.setId(existing.getId());
        editRequest.setProjectId(request.getProjectId());
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
        agentExecLogService.audit("BUG_UPDATE", bug.getId(), JSON.toJSONString(request));
        return get(bug.getId());
    }

    public void relateCase(AgentBugRelateCaseRequest request) {
        String userId = requireUserId();
        AssociateOtherCaseRequest associate = new AssociateOtherCaseRequest();
        associate.setProjectId(request.getProjectId());
        associate.setSourceId(request.getBugId());
        associate.setSourceType(StringUtils.defaultIfBlank(request.getCaseType(), CaseType.FUNCTIONAL_CASE.getKey()));
        associate.setSelectIds(request.getCaseIds());
        associate.setSelectAll(false);
        bugRelateCaseCommonService.relateCase(associate, false, userId);
        agentExecLogService.audit("BUG_RELATE_CASE", request.getBugId(), JSON.toJSONString(request));
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
            throw new MSException("项目不存在: " + projectId);
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
                throw new MSException("项目未配置缺陷默认模板");
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
