package io.metersphere.system.controller;

import io.metersphere.ai.engine.common.AIModelType;
import io.metersphere.sdk.constants.InternalUser;
import io.metersphere.system.base.BaseTest;
import io.metersphere.system.constants.AIConfigConstants;
import io.metersphere.system.domain.AiModelSource;
import io.metersphere.system.dto.request.ai.AiModelSourceDTO;
import io.metersphere.system.dto.request.ai.AiProjectGovernanceDTO;
import io.metersphere.system.dto.request.ai.AiProviderChatRequest;
import io.metersphere.system.dto.request.ai.AiProviderInvocationResult;
import io.metersphere.system.service.SystemAIConfigService;
import io.metersphere.system.service.ai.AiGovernanceService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AiProviderEndToEndTests extends BaseTest {
    @Value("${embedded.mockserver.host}")
    private String host;
    @Value("${embedded.mockserver.port}")
    private int port;
    @Resource
    private SystemAIConfigService configService;
    @Resource
    private AiGovernanceService governanceService;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    protected String getBasePath() {
        return "/ai/provider/";
    }

    @Test
    void invokeTraversesHttpSecurityProviderUsageAndAuditPersistence() throws Exception {
        mockPost("/v1/chat/completions", """
                {
                  "id":"chatcmpl-ai-e2e","object":"chat.completion","created":1,"model":"e2e-model",
                  "choices":[{"index":0,"message":{"role":"assistant","content":"generated-e2e"},"finish_reason":"stop"}],
                  "usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}
                }
                """);
        AiModelSource model = createModel();
        try {
            AiProjectGovernanceDTO governance = new AiProjectGovernanceDTO();
            governance.setProjectId(DEFAULT_PROJECT_ID);
            governance.setAllowedModelIds(List.of(model.getId()));
            governance.setFallbackModelId(model.getId());
            governanceService.save(governance, InternalUser.ADMIN.getValue());

            AiProviderChatRequest request = new AiProviderChatRequest();
            request.setProjectId(DEFAULT_PROJECT_ID);
            request.setOrganizationId(DEFAULT_ORGANIZATION_ID);
            request.setChatModelId(model.getId());
            request.setConversationId(UUID.randomUUID().toString());
            request.setPrompt("generate test cases");
            MvcResult mvcResult = requestPostWithOkAndReturn("invoke", request);
            AiProviderInvocationResult result = getResultData(mvcResult, AiProviderInvocationResult.class);

            Assertions.assertEquals("generated-e2e", result.getContent());
            Assertions.assertEquals(18L, result.getTotalTokens());
            Integer usage = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1) FROM ai_provider_usage
                    WHERE project_id=? AND model_source_id=? AND total_tokens=18 AND success=1
                    """, Integer.class, DEFAULT_PROJECT_ID, model.getId());
            Assertions.assertEquals(1, usage);
            Integer audit = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1) FROM operation_log
                    WHERE project_id=? AND source_id=? AND module='AI_PROVIDER_GOVERNANCE'
                    """, Integer.class, DEFAULT_PROJECT_ID, model.getId());
            Assertions.assertTrue(audit != null && audit > 0);
        } finally {
            jdbcTemplate.update("DELETE FROM ai_provider_usage WHERE model_source_id=?", model.getId());
            jdbcTemplate.update("DELETE FROM ai_project_governance WHERE project_id=?", DEFAULT_PROJECT_ID);
            jdbcTemplate.update("DELETE FROM ai_model_source WHERE id=?", model.getId());
        }
    }

    private AiModelSource createModel() {
        AiModelSourceDTO dto = new AiModelSourceDTO();
        dto.setName("ai-e2e-" + UUID.randomUUID());
        dto.setType("LLM");
        dto.setProviderName(AIModelType.DEEP_SEEK);
        dto.setPermissionType(AIConfigConstants.AiPermissionType.PRIVATE.toString());
        dto.setOwnerType(AIConfigConstants.AiOwnerType.PERSONAL.toString());
        dto.setBaseName("e2e-model");
        dto.setApiUrl("http://" + host + ":" + port);
        dto.setAppKey("test-key-not-logged");
        dto.setStatus(true);
        dto.setAdvSettingDTOList(List.of());
        return configService.editModuleConfig(dto, InternalUser.ADMIN.getValue());
    }
}
