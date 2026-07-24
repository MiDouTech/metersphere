package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentBugCreateRequest;
import io.metersphere.agent.dto.AgentBugDTO;
import io.metersphere.agent.dto.AgentBugRelateCaseRequest;
import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.dto.request.BugEditRequest;
import io.metersphere.bug.dto.response.BugCustomFieldDTO;
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
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    public AgentBugDTO create(AgentBugCreateRequest request) {
        String userId = requireUserId();
        Project project = projectMapper.selectByPrimaryKey(request.getProjectId());
        if (project == null) {
            throw new MSException("项目不存在: " + request.getProjectId());
        }
        String scene = TemplateScene.BUG.name();
        TemplateDTO templateDTO;
        String templateId = request.getTemplateId();
        if (StringUtils.isNotBlank(templateId)) {
            templateDTO = projectTemplateService.getTemplateDTOById(templateId, request.getProjectId(), scene);
        } else {
            // 未设置 ProjectApplication 默认模板时，回落到项目内置模板
            templateDTO = projectTemplateService.getDefaultTemplateDTO(request.getProjectId(), scene);
            if (templateDTO == null || StringUtils.isBlank(templateDTO.getId())) {
                throw new MSException("项目未配置缺陷默认模板");
            }
            templateId = templateDTO.getId();
        }

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
