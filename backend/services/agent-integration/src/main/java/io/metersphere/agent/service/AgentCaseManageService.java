package io.metersphere.agent.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.constants.AgentErrorCode;
import io.metersphere.agent.dto.AgentCaseStepDTO;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.functional.constants.FunctionalCaseTypeConstants;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseBlob;
import io.metersphere.functional.dto.CaseCustomFieldDTO;
import io.metersphere.functional.dto.FunctionalCaseStepDTO;
import io.metersphere.functional.mapper.FunctionalCaseBlobMapper;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.request.FunctionalCaseEditRequest;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.project.service.ProjectTemplateService;
import io.metersphere.sdk.constants.TemplateScene;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentCaseManageService {
    @Resource
    private FunctionalCaseService functionalCaseService;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private FunctionalCaseBlobMapper functionalCaseBlobMapper;
    @Resource
    private ProjectTemplateService projectTemplateService;
    @Resource
    private AgentProjectService agentProjectService;
    @Resource
    private AgentCaseSchemaMapper agentCaseSchemaMapper;
    @Resource
    private AgentExecLogService agentExecLogService;

    public Object getTemplate(String projectId, String templateId) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        assertProjectAllowed(resolvedProjectId);
        TemplateDTO templateDTO;
        if (StringUtils.isNotBlank(templateId)) {
            templateDTO = projectTemplateService.getTemplateDTOById(templateId, resolvedProjectId, TemplateScene.FUNCTIONAL.name());
        } else {
            templateDTO = projectTemplateService.getDefaultTemplateDTO(resolvedProjectId, TemplateScene.FUNCTIONAL.name());
        }
        if (templateDTO == null) {
            throw new MSException(AgentErrorCode.RESOURCE_NOT_FOUND, "功能用例模板不存在");
        }
        return toTemplateView(templateDTO);
    }

    public Object listHistory(String projectId, String caseId, Integer current, Integer pageSize) {
        FunctionalCase functionalCase = requireCaseInProject(projectId, caseId);
        OperationHistoryRequest request = new OperationHistoryRequest();
        request.setProjectId(functionalCase.getProjectId());
        request.setSourceId(functionalCase.getId());
        request.setCurrent(current == null || current < 1 ? 1 : current);
        request.setPageSize(AgentConstants.normalizePageSize(pageSize));
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize(), "id desc");
        List<OperationHistoryDTO> list = functionalCaseService.operationHistoryList(request);
        Pager<List<OperationHistoryDTO>> pager = PageUtils.setPageInfo(page, list);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pager.getTotal());
        result.put("list", list);
        return result;
    }

    @SuppressWarnings("unchecked")
    public Object update(Map<String, Object> arguments) {
        String projectId = requiredString(arguments, "projectId");
        String caseId = requiredString(arguments, "caseId");
        FunctionalCase existing = requireCaseInProject(projectId, caseId);
        Long expectedUpdateTime = toLong(arguments.get("expectedUpdateTime"));
        if (expectedUpdateTime != null && existing.getUpdateTime() != null
                && !expectedUpdateTime.equals(existing.getUpdateTime())) {
            throw new MSException(AgentErrorCode.VERSION_CONFLICT,
                    "用例已被他人更新，当前 updateTime=" + existing.getUpdateTime());
        }

        Map<String, Object> patch = arguments.get("patch") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        FunctionalCaseBlob blob = functionalCaseBlobMapper.selectByPrimaryKey(existing.getId());
        FunctionalCaseEditRequest edit = new FunctionalCaseEditRequest();
        edit.setId(existing.getId());
        edit.setProjectId(existing.getProjectId());
        edit.setTemplateId(existing.getTemplateId());
        edit.setModuleId(existing.getModuleId());
        edit.setName(existing.getName());
        edit.setCaseEditType(StringUtils.defaultIfBlank(existing.getCaseEditType(),
                FunctionalCaseTypeConstants.CaseEditType.STEP.name()));
        edit.setTags(existing.getTags());
        edit.setVersionId(existing.getVersionId());

        if (blob != null) {
            edit.setPrerequisite(bytesToString(blob.getPrerequisite()));
            edit.setDescription(bytesToString(blob.getDescription()));
            edit.setTextDescription(bytesToString(blob.getTextDescription()));
            edit.setExpectedResult(bytesToString(blob.getExpectedResult()));
            edit.setSteps(bytesToString(blob.getSteps()));
        }

        if (patch.containsKey("name") && patch.get("name") != null) {
            edit.setName(String.valueOf(patch.get("name")));
        }
        if (patch.containsKey("moduleId") && patch.get("moduleId") != null) {
            edit.setModuleId(String.valueOf(patch.get("moduleId")));
        }
        if (patch.containsKey("prerequisite")) {
            edit.setPrerequisite(patch.get("prerequisite") == null ? "" : String.valueOf(patch.get("prerequisite")));
        }
        if (patch.containsKey("description")) {
            edit.setDescription(patch.get("description") == null ? "" : String.valueOf(patch.get("description")));
        }
        if (patch.containsKey("tags")) {
            if (patch.get("tags") == null) {
                edit.setTags(null);
            } else {
                edit.setTags(JSON.parseArray(JSON.toJSONString(patch.get("tags")), String.class));
            }
        }
        if (patch.containsKey("steps")) {
            List<AgentCaseStepDTO> steps = JSON.parseArray(JSON.toJSONString(patch.get("steps")), AgentCaseStepDTO.class);
            edit.setCaseEditType(FunctionalCaseTypeConstants.CaseEditType.STEP.name());
            edit.setSteps(toStepsJson(steps));
        }

        TemplateDTO templateDTO = projectTemplateService.getTemplateDTOById(
                existing.getTemplateId(), existing.getProjectId(), TemplateScene.FUNCTIONAL.name());
        Map<String, String> customFieldMap = new LinkedHashMap<>();
        if (patch.get("customFields") instanceof Map<?, ?> customMap) {
            customMap.forEach((k, v) -> {
                if (k != null) {
                    customFieldMap.put(String.valueOf(k), v == null ? null : String.valueOf(v));
                }
            });
        }
        if (patch.containsKey("priority") && patch.get("priority") != null && templateDTO != null) {
            for (TemplateCustomFieldDTO field : templateDTO.getCustomFields()) {
                if (StringUtils.equalsIgnoreCase(field.getInternalFieldKey(), "functional_priority")) {
                    customFieldMap.put(field.getFieldId(), String.valueOf(patch.get("priority")));
                }
            }
        }
        if (!customFieldMap.isEmpty() && templateDTO != null) {
            List<CaseCustomFieldDTO> fields = new ArrayList<>();
            for (TemplateCustomFieldDTO field : templateDTO.getCustomFields()) {
                if (customFieldMap.containsKey(field.getFieldId())) {
                    CaseCustomFieldDTO dto = new CaseCustomFieldDTO();
                    dto.setFieldId(field.getFieldId());
                    dto.setValue(customFieldMap.get(field.getFieldId()));
                    fields.add(dto);
                }
            }
            edit.setCustomFields(fields);
        }

        String userId = SessionUtils.getUserId();
        FunctionalCase updated = functionalCaseService.updateFunctionalCase(edit, new ArrayList<>(), userId);

        Map<String, Object> audit = new HashMap<>();
        audit.put("caseId", caseId);
        audit.put("reason", arguments.get("reason"));
        audit.put("patch", patch);
        agentExecLogService.audit("CASE_UPDATE", caseId, JSON.toJSONString(audit));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseId", updated.getId());
        result.put("name", updated.getName());
        result.put("updateTime", updated.getUpdateTime());
        result.put("moduleId", updated.getModuleId());
        return result;
    }

    private String toStepsJson(List<AgentCaseStepDTO> steps) {
        List<FunctionalCaseStepDTO> functionalSteps = agentCaseSchemaMapper.toFunctionalCaseSteps(steps);
        if (CollectionUtils.isEmpty(functionalSteps)) {
            return JSON.toJSONString(new ArrayList<>());
        }
        for (FunctionalCaseStepDTO step : functionalSteps) {
            if (StringUtils.isBlank(step.getId())) {
                step.setId(IDGenerator.nextStr());
            }
        }
        return JSON.toJSONString(functionalSteps);
    }

    private Map<String, Object> toTemplateView(TemplateDTO templateDTO) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", templateDTO.getId());
        view.put("name", templateDTO.getName());
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
                fields.add(item);
            }
        }
        view.put("customFields", fields);
        return view;
    }

    private FunctionalCase requireCaseInProject(String projectId, String caseId) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        assertProjectAllowed(resolvedProjectId);
        FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(caseId);
        if (functionalCase == null || Boolean.TRUE.equals(functionalCase.getDeleted())) {
            throw new MSException(AgentErrorCode.RESOURCE_NOT_FOUND, "用例不存在: " + caseId);
        }
        if (!StringUtils.equals(functionalCase.getProjectId(), resolvedProjectId)) {
            throw new MSException(AgentErrorCode.RESOURCE_PROJECT_MISMATCH, "用例不属于指定项目");
        }
        return functionalCase;
    }

    private void assertProjectAllowed(String projectId) {
        AgentToken token = AgentTokenContext.get();
        if (token != null && !AgentTokenProjectAccess.allows(token, projectId)) {
            throw new MSException(AgentErrorCode.PROJECT_NOT_ALLOWED, "Token 无权访问项目: " + projectId);
        }
    }

    private String requiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            throw new MSException("Missing required argument: " + key);
        }
        return String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (StringUtils.isBlank(text)) {
            return null;
        }
        return Long.parseLong(text);
    }

    private String bytesToString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
