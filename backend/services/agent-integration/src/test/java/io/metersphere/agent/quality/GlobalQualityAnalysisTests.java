package io.metersphere.agent.quality;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.constants.PermissionConstants;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class GlobalQualityAnalysisTests {
    @Test void trialKeepsLegacyFactsUnavailableAndOnlyPersistsSeparateTrialSummary(){
        var jdbc=mock(JdbcTemplate.class);var access=mock(QualityPolicyAccess.class);var validator=new QualityPolicyValidator();
        var analysis=new GlobalQualityAnalysisService(jdbc,access,validator,mock(AgentExecutionMapper.class));
        var valid=validator.validate(QualityPolicyValidatorTests.VALID);
        when(access.requireSystem(PermissionConstants.SYSTEM_QUALITY_MANAGE)).thenReturn("admin");
        when(jdbc.queryForList(contains("execution_quality_policy_global WHERE"),eq("p1"))).thenReturn(List.of(Map.of("rules_json",valid.normalizedJson(),"content_hash",valid.contentHash())));
        when(jdbc.queryForList(contains("FROM ai_execution_step_result"))).thenReturn(List.of(Map.of("id","legacy-result")));
        var result=analysis.trial("p1");assertEquals(1,result.get("unavailable"));assertEquals(0,result.get("wouldPass"));
        verify(jdbc).update(startsWith("INSERT INTO ai_quality_policy_trial"),any(Object[].class));
        verify(jdbc,never()).update(startsWith("UPDATE"),any(Object[].class));
        verify(access).requireSystem(PermissionConstants.SYSTEM_QUALITY_MANAGE);
    }
}
