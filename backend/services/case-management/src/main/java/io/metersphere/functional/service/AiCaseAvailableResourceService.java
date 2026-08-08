package io.metersphere.functional.service;

import io.metersphere.functional.constants.AiResourceType;
import io.metersphere.functional.dto.AiCaseAvailableModelDTO;
import io.metersphere.functional.dto.AiResourceCapabilities;
import io.metersphere.functional.dto.AiResourceSelection;
import io.metersphere.functional.dto.AiSelectableResourceDTO;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.ai.agent.AiUserAgentConnectionDTO;
import io.metersphere.system.dto.request.ai.AiProjectGovernanceDTO;
import io.metersphere.system.service.ai.AiGovernanceService;
import io.metersphere.system.service.ai.agent.AiUserAgentFeatureService;
import io.metersphere.system.service.ai.agent.AiUserAgentService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiCaseAvailableResourceService {
    private final AiCaseAvailableModelService modelService;
    private final AiUserAgentService userAgentService;
    private final AiUserAgentFeatureService featureService;
    private final AiGovernanceService governanceService;

    public AiCaseAvailableResourceService(AiCaseAvailableModelService modelService,
                                          AiUserAgentService userAgentService,
                                          AiUserAgentFeatureService featureService,
                                          AiGovernanceService governanceService) {
        this.modelService = modelService;
        this.userAgentService = userAgentService;
        this.featureService = featureService;
        this.governanceService = governanceService;
    }

    public List<AiSelectableResourceDTO> list(String projectId, String userId) {
        List<AiSelectableResourceDTO> resources = new ArrayList<>();
        modelService.list(projectId, userId).stream().map(this::fromModel).forEach(resources::add);
        if (!featureService.enabled()) {
            return resources;
        }
        AiProjectGovernanceDTO governance = governanceService.get(projectId);
        userAgentService.listConnections(userId).stream()
                .map(connection -> fromConnection(connection, governance))
                .forEach(resources::add);
        return resources;
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
        if (StringUtils.isNotBlank(legacyModelSourceId)) {
            throw new MSException("USER_AGENT 请求不得同时指定 modelSourceId");
        }
        AiUserAgentConnectionDTO connection = userAgentService.requireAvailable(normalizedId, userId);
        governanceService.assertAgentAllowed(projectId, connection.getProvider());
        return new AiResourceSelection(type.name(), connection.getId(), null, connection.getId(),
                connection.getProvider(), capability(connection, "tools"));
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

    private AiSelectableResourceDTO fromConnection(AiUserAgentConnectionDTO connection,
                                                    AiProjectGovernanceDTO governance) {
        AiSelectableResourceDTO dto = new AiSelectableResourceDTO();
        dto.setId(connection.getId());
        dto.setResourceType(AiResourceType.USER_AGENT.name());
        dto.setProvider(connection.getProvider());
        dto.setDisplayName(connection.getDisplayName());
        dto.setPersonal(true);
        dto.setExperimental(!StringUtils.equalsIgnoreCase(connection.getProvider(), "WORKBUDDY"));
        dto.setConnectionStatus(connection.getStatus());
        dto.setOnline(StringUtils.equals(connection.getStatus(), "CONNECTED")
                && StringUtils.equals(connection.getDeviceStatus(), "ONLINE"));
        if (!governance.isAllowPersonalAgent() || !governance.getAllowedResourceTypes().contains("USER_AGENT")) {
            dto.setUnavailableReason("AI_RESOURCE_NOT_ALLOWED");
        } else if (!governance.getAllowedAgentProviders().isEmpty()
                && governance.getAllowedAgentProviders().stream()
                .noneMatch(item -> StringUtils.equalsIgnoreCase(item, connection.getProvider()))) {
            dto.setUnavailableReason("AI_RESOURCE_NOT_ALLOWED");
        } else if (StringUtils.equals(connection.getStatus(), "AUTH_EXPIRED")) {
            dto.setUnavailableReason("AGENT_AUTH_EXPIRED");
        } else if (!dto.isOnline()) {
            dto.setUnavailableReason("AGENT_OFFLINE");
        }
        AiResourceCapabilities capabilities = new AiResourceCapabilities();
        capabilities.setStream(capability(connection, "stream"));
        capabilities.setTools(capability(connection, "tools"));
        capabilities.setFiles(capability(connection, "files"));
        capabilities.setCancel(capability(connection, "cancel"));
        dto.setCapabilities(capabilities);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private boolean capability(AiUserAgentConnectionDTO connection, String name) {
        try {
            Map<String, Object> values = JSON.parseObject(StringUtils.defaultIfBlank(
                    connection.getCapabilities(), "{}"), Map.class);
            return Boolean.TRUE.equals(values.get(name));
        } catch (Exception ignored) {
            return false;
        }
    }
}
