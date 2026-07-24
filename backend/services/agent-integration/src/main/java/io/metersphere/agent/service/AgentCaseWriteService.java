package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseBatchCreateRequest;
import io.metersphere.agent.dto.AgentCaseBatchCreateResponse;
import io.metersphere.agent.dto.AgentCaseCreateItem;
import io.metersphere.agent.dto.AgentCaseCreateRequest;
import io.metersphere.agent.dto.AgentCaseStepDTO;
import io.metersphere.agent.dto.AgentModuleCreateRequest;
import io.metersphere.agent.dto.AgentModuleDTO;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.functional.constants.FunctionalCaseTypeConstants;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseModule;
import io.metersphere.functional.domain.FunctionalCaseModuleExample;
import io.metersphere.functional.dto.CaseCustomFieldDTO;
import io.metersphere.functional.dto.FunctionalCaseStepDTO;
import io.metersphere.functional.mapper.FunctionalCaseModuleMapper;
import io.metersphere.functional.request.FunctionalCaseAddRequest;
import io.metersphere.functional.request.FunctionalCaseModuleCreateRequest;
import io.metersphere.functional.service.FunctionalCaseModuleService;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.project.service.ProjectTemplateService;
import io.metersphere.sdk.constants.ModuleConstants;
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
import java.util.Collections;
import java.util.List;

@Service
public class AgentCaseWriteService {
    @Resource
    private FunctionalCaseService functionalCaseService;
    @Resource
    private FunctionalCaseModuleService functionalCaseModuleService;
    @Resource
    private FunctionalCaseModuleMapper functionalCaseModuleMapper;
    @Resource
    private ProjectTemplateService projectTemplateService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private AgentCaseSchemaMapper agentCaseSchemaMapper;
    @Resource
    private AgentExecLogService agentExecLogService;

    public AgentModuleDTO createModule(AgentModuleCreateRequest request) {
        String userId = requireUserId();
        String moduleId;
        if (StringUtils.isNotBlank(request.getModulePath())) {
            moduleId = resolveOrCreateModulePath(request.getProjectId(), request.getModulePath(), userId);
        } else {
            FunctionalCaseModuleCreateRequest createRequest = new FunctionalCaseModuleCreateRequest();
            createRequest.setProjectId(request.getProjectId());
            createRequest.setName(request.getName());
            createRequest.setParentId(StringUtils.defaultIfBlank(request.getParentId(), ModuleConstants.ROOT_NODE_PARENT_ID));
            moduleId = functionalCaseModuleService.add(createRequest, userId);
        }
        AgentModuleDTO dto = new AgentModuleDTO();
        dto.setId(moduleId);
        dto.setName(request.getName());
        dto.setPath(request.getModulePath());
        dto.setParentId(request.getParentId());
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentCaseBatchCreateResponse.CreatedCase createCase(AgentCaseCreateRequest request) {
        AgentCaseBatchCreateRequest batch = new AgentCaseBatchCreateRequest();
        batch.setProjectId(request.getProjectId());
        batch.setModuleId(request.getModuleId());
        batch.setModulePath(request.getModulePath());
        batch.setTemplateId(request.getTemplateId());
        batch.setCases(Collections.singletonList(request.toItem()));
        batch.setFailFast(true);
        AgentCaseBatchCreateResponse response = batchCreate(batch);
        if (CollectionUtils.isNotEmpty(response.getErrors())) {
            throw new MSException(response.getErrors().get(0).getError());
        }
        return response.getCreated().get(0);
    }

    public AgentCaseBatchCreateResponse batchCreate(AgentCaseBatchCreateRequest request) {
        String userId = requireUserId();
        Project project = projectMapper.selectByPrimaryKey(request.getProjectId());
        if (project == null) {
            throw new MSException("项目不存在: " + request.getProjectId());
        }
        String organizationId = project.getOrganizationId();
        String moduleId = resolveModuleId(request.getProjectId(), request.getModuleId(), request.getModulePath(), userId);
        String scene = TemplateScene.FUNCTIONAL.name();
        TemplateDTO templateDTO;
        String templateId = request.getTemplateId();
        if (StringUtils.isNotBlank(templateId)) {
            templateDTO = projectTemplateService.getTemplateDTOById(templateId, request.getProjectId(), scene);
        } else {
            // 未设置 ProjectApplication 默认模板时，回落到项目内置模板（与 getDefaultTemplateDTO 一致）
            templateDTO = projectTemplateService.getDefaultTemplateDTO(request.getProjectId(), scene);
            if (templateDTO == null || StringUtils.isBlank(templateDTO.getId())) {
                throw new MSException("项目未配置功能用例默认模板");
            }
            templateId = templateDTO.getId();
        }

        AgentCaseBatchCreateResponse response = new AgentCaseBatchCreateResponse();
        boolean failFast = Boolean.TRUE.equals(request.getFailFast());
        for (AgentCaseCreateItem item : request.getCases()) {
            try {
                FunctionalCase created = createOne(request.getProjectId(), organizationId, moduleId, templateId, templateDTO, item, userId);
                AgentCaseBatchCreateResponse.CreatedCase createdCase = new AgentCaseBatchCreateResponse.CreatedCase();
                createdCase.setCaseId(created.getId());
                createdCase.setNum(created.getNum());
                createdCase.setName(created.getName());
                createdCase.setModuleId(moduleId);
                response.getCreated().add(createdCase);
            } catch (Exception ex) {
                AgentCaseBatchCreateResponse.FailedCase failed = new AgentCaseBatchCreateResponse.FailedCase();
                failed.setName(item.getName());
                failed.setError(ex.getMessage());
                response.getErrors().add(failed);
                if (failFast) {
                    break;
                }
            }
        }
        agentExecLogService.audit("CASE_BATCH_CREATE", request.getProjectId(),
                JSON.toJSONString(response));
        return response;
    }

    private FunctionalCase createOne(String projectId, String organizationId, String moduleId, String templateId,
                                     TemplateDTO templateDTO, AgentCaseCreateItem item, String userId) {
        FunctionalCaseAddRequest addRequest = new FunctionalCaseAddRequest();
        addRequest.setProjectId(projectId);
        addRequest.setModuleId(moduleId);
        addRequest.setTemplateId(templateId);
        addRequest.setName(item.getName());
        addRequest.setPrerequisite(StringUtils.defaultString(item.getPrerequisite()));
        addRequest.setDescription(StringUtils.defaultString(item.getDescription()));
        addRequest.setTags(item.getTags());
        addRequest.setCaseEditType(FunctionalCaseTypeConstants.CaseEditType.STEP.name());
        addRequest.setSteps(toStepsJson(item.getSteps()));
        addRequest.setCustomFields(buildPriorityFields(templateDTO, item.getPriority(), userId));
        addRequest.setAiCreate(false);
        return functionalCaseService.addFunctionalCase(addRequest, new ArrayList<>(), userId, organizationId);
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

    private List<CaseCustomFieldDTO> buildPriorityFields(TemplateDTO templateDTO, String priority, String userId) {
        List<CaseCustomFieldDTO> fields = new ArrayList<>();
        if (templateDTO == null || CollectionUtils.isEmpty(templateDTO.getCustomFields())) {
            return fields;
        }
        for (TemplateCustomFieldDTO field : templateDTO.getCustomFields()) {
            CaseCustomFieldDTO dto = new CaseCustomFieldDTO();
            dto.setFieldId(field.getFieldId());
            if (StringUtils.equalsIgnoreCase(field.getInternalFieldKey(), "functional_priority")) {
                dto.setValue(StringUtils.defaultIfBlank(priority, "P0"));
            } else if (field.getDefaultValue() != null) {
                String defaultValue = String.valueOf(field.getDefaultValue());
                if (StringUtils.contains(defaultValue, "CREATE_USER")) {
                    dto.setValue(userId);
                } else {
                    dto.setValue(defaultValue);
                }
            } else {
                continue;
            }
            fields.add(dto);
        }
        return fields;
    }

    private String resolveModuleId(String projectId, String moduleId, String modulePath, String userId) {
        if (StringUtils.isNotBlank(moduleId)) {
            return moduleId;
        }
        if (StringUtils.isNotBlank(modulePath)) {
            return resolveOrCreateModulePath(projectId, modulePath, userId);
        }
        return ModuleConstants.DEFAULT_NODE_ID;
    }

    public String resolveOrCreateModulePath(String projectId, String modulePath, String userId) {
        String[] parts = StringUtils.split(modulePath, "/");
        if (parts == null || parts.length == 0) {
            return ModuleConstants.DEFAULT_NODE_ID;
        }
        String parentId = ModuleConstants.ROOT_NODE_PARENT_ID;
        String currentId = ModuleConstants.DEFAULT_NODE_ID;
        for (String part : parts) {
            String name = StringUtils.trim(part);
            if (StringUtils.isBlank(name)) {
                continue;
            }
            FunctionalCaseModule existing = findChildModule(projectId, parentId, name);
            if (existing != null) {
                currentId = existing.getId();
                parentId = existing.getId();
                continue;
            }
            FunctionalCaseModuleCreateRequest createRequest = new FunctionalCaseModuleCreateRequest();
            createRequest.setProjectId(projectId);
            createRequest.setName(name);
            createRequest.setParentId(parentId);
            currentId = functionalCaseModuleService.add(createRequest, userId);
            parentId = currentId;
        }
        return currentId;
    }

    private FunctionalCaseModule findChildModule(String projectId, String parentId, String name) {
        FunctionalCaseModuleExample example = new FunctionalCaseModuleExample();
        example.createCriteria().andProjectIdEqualTo(projectId).andParentIdEqualTo(parentId).andNameEqualTo(name);
        List<FunctionalCaseModule> modules = functionalCaseModuleMapper.selectByExample(example);
        return CollectionUtils.isEmpty(modules) ? null : modules.get(0);
    }

    private String requireUserId() {
        String userId = SessionUtils.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new MSException("无法解析 Agent Token 对应用户");
        }
        return userId;
    }
}
