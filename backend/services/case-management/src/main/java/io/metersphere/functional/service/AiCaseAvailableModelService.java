package io.metersphere.functional.service;

import io.metersphere.functional.dto.AiCaseAvailableModelDTO;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.service.ai.AiGovernanceService;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiCaseAvailableModelService {
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private AiGovernanceService aiGovernanceService;

    public List<AiCaseAvailableModelDTO> list(String projectId, String userId) {
        List<String> allowed = aiGovernanceService.get(projectId).getAllowedModelIds();
        return jdbcTemplate.queryForList("""
                        SELECT id, name, type, provider_name, owner, owner_type, base_name, adv_settings
                        FROM ai_model_source
                        WHERE status=1 AND (owner='system' OR owner=?)
                        ORDER BY permission_type ASC, name ASC
                        """, userId).stream()
                .filter(row -> CollectionUtils.isEmpty(allowed) || allowed.contains((String) row.get("id")))
                .map(row -> toModel(row, userId))
                .toList();
    }

    public AiCaseAvailableModelDTO requireAllowed(String projectId, String modelSourceId, String userId) {
        if (StringUtils.isAnyBlank(projectId, modelSourceId, userId)) {
            throw new MSException("模型、项目和用户不能为空");
        }
        return list(projectId, userId).stream()
                .filter(model -> StringUtils.equals(modelSourceId, model.getId()))
                .findFirst()
                .orElseThrow(() -> new MSException("模型不存在、未启用、无权使用或不在项目白名单中"));
    }

    private AiCaseAvailableModelDTO toModel(Map<String, Object> row, String userId) {
        AiCaseAvailableModelDTO dto = new AiCaseAvailableModelDTO();
        dto.setId((String) row.get("id"));
        dto.setName((String) row.get("name"));
        dto.setType((String) row.get("type"));
        dto.setProvider((String) row.get("provider_name"));
        dto.setBaseName((String) row.get("base_name"));
        dto.setPersonal(StringUtils.equals(userId, (String) row.get("owner")));
        dto.setSupportsStream(true);
        dto.setSupportsTools(List.of("Open AI", "DeepSeek", "ZhiPu AI")
                .stream().anyMatch(provider -> StringUtils.equalsIgnoreCase(provider, dto.getProvider())));
        dto.setSupportsVision(StringUtils.containsIgnoreCase(dto.getType(), "视觉"));
        dto.setConnectionStatus("CONNECTED");
        applyAdvancedCapabilities(dto, (String) row.get("adv_settings"));
        return dto;
    }

    private void applyAdvancedCapabilities(AiCaseAvailableModelDTO dto, String rawSettings) {
        if (StringUtils.isBlank(rawSettings)) {
            return;
        }
        // Existing model settings are provider-specific. Capability values remain conservative until
        // the provider adapter explicitly declares native tool/context support.
        if (StringUtils.containsIgnoreCase(rawSettings, "vision")) {
            dto.setSupportsVision(true);
        }
    }
}
