package io.metersphere.plan.service;

import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.service.FunctionalExecutionViewProvider;
import io.metersphere.plan.dto.response.TestPlanCaseDetailResponse;
import io.metersphere.sdk.constants.TestPlanConstants;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/** Active plans share current source state. Historical facts stay separate from authoring steps. */
@Service
public class PlanCaseEffectiveViewService {
    private final JdbcTemplate jdbc;
    private final List<FunctionalExecutionViewProvider> providers;

    public PlanCaseEffectiveViewService(JdbcTemplate jdbc, List<FunctionalExecutionViewProvider> providers) {
        this.jdbc = jdbc;
        this.providers = providers;
    }

    public boolean followsSource(String planId) {
        var rows = jdbc.queryForList("SELECT status<>? FROM test_plan WHERE id=?", Boolean.class,
                TestPlanConstants.TEST_PLAN_STATUS_ARCHIVED, planId);
        if (rows.isEmpty()) throw new io.metersphere.sdk.exception.MSException("测试计划不存在或已删除");
        return Boolean.TRUE.equals(rows.getFirst());
    }

    public void requireManualBugRelation(String relationId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM plan_case_effective_bug_relation
                 WHERE id=? AND sync_origin='SOURCE_SYNC'
                """, Long.class, relationId);
        if (count != null && count > 0) {
            throw new io.metersphere.sdk.exception.MSException("关联来自源用例，请在源用例中取消关联");
        }
    }

    public void enrich(TestPlanCaseDetailResponse response, FunctionalCaseDetailDTO source) {
        response.setLastExecuteResult(source.getLastExecuteResult());
        response.setLastExecuteTime(source.getLastExecuteTime());
        response.setLastExecuteUser(source.getLastExecuteUser());
        response.setLastExecuteUserName(source.getLastExecuteUserName());
        response.setSyncState("CURRENT");
        for (var provider : providers) {
            var view = provider.current(source);
            if (view.isPresent()) {
                response.setExecutionRef(Map.of("type", view.get().type(), "id", view.get().id()));
                response.setExecutionFacts(view.get().facts());
                return;
            }
        }
        // Ordinary execution facts already live in source.steps. Do not fabricate a submission ID.
        response.setExecutionRef(Map.of("type", "SOURCE_CASE", "id", source.getId()));
    }
}
