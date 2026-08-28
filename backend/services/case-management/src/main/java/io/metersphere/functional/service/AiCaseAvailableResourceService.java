package io.metersphere.functional.service;

import io.metersphere.functional.constants.AiResourceType;
import io.metersphere.functional.dto.AiCaseAvailableModelDTO;
import io.metersphere.functional.dto.AiResourceCapabilities;
import io.metersphere.functional.dto.AiResourceSelection;
import io.metersphere.functional.dto.AiSelectableResourceDTO;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiCaseAvailableResourceService {
    private final AiCaseAvailableModelService modelService;

    public AiCaseAvailableResourceService(AiCaseAvailableModelService modelService) {
        this.modelService = modelService;
    }

    public List<AiSelectableResourceDTO> list(String projectId, String userId) {
        return modelService.list(projectId, userId).stream().map(this::fromModel).toList();
    }

    public AiResourceSelection requireAllowed(String projectId, String resourceType, String resourceId,
                                              String legacyModelSourceId, String userId) {
        String normalizedType = StringUtils.upperCase(StringUtils.trimToNull(resourceType));
        String normalizedId = StringUtils.trimToNull(resourceId);
        if (normalizedType == null && normalizedId == null) {
            normalizedType = AiResourceType.MODEL_API.name();
            normalizedId = StringUtils.trimToNull(legacyModelSourceId);
        }
        if (normalizedType == null || normalizedId == null) {
            throw new MSException("resourceType 与 resourceId 必须同时提供");
        }
        AiResourceType type;
        try {
            type = AiResourceType.valueOf(normalizedType);
        } catch (IllegalArgumentException error) {
            throw new MSException("不支持的 AI 资源类型");
        }
        if (type == AiResourceType.MODEL_API) {
            if (StringUtils.isNotBlank(legacyModelSourceId)
                    && !StringUtils.equals(normalizedId, legacyModelSourceId)) {
                throw new MSException("resourceId 与 modelSourceId 冲突");
            }
            AiCaseAvailableModelDTO model = modelService.requireAllowed(projectId, normalizedId, userId);
            return new AiResourceSelection(type.name(), model.getId(), model.getId(), null,
                    model.getProvider(), model.isSupportsTools());
        }
        throw new MSException("AGENT_BRIDGE_DEPRECATED：个人 Agent 不再作为平台 AI 资源，请通过 Remote MCP 执行个人任务");
    }

    private AiSelectableResourceDTO fromModel(AiCaseAvailableModelDTO model) {
        AiSelectableResourceDTO dto = new AiSelectableResourceDTO();
        dto.setId(model.getId());
        dto.setResourceType(AiResourceType.MODEL_API.name());
        dto.setProvider(model.getProvider());
        dto.setDisplayName(model.getName());
        dto.setPersonal(model.isPersonal());
        dto.setOnline(true);
        dto.setConnectionStatus(model.getConnectionStatus());
        AiResourceCapabilities capabilities = new AiResourceCapabilities();
        capabilities.setStream(model.isSupportsStream());
        capabilities.setTools(model.isSupportsTools());
        capabilities.setFiles(true);
        capabilities.setCancel(true);
        capabilities.setVision(model.isSupportsVision());
        capabilities.setContextWindow(model.getContextWindow());
        capabilities.setMaxOutputTokens(model.getMaxOutputTokens());
        dto.setCapabilities(capabilities);
        return dto;
    }

}
