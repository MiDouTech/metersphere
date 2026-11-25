package io.metersphere.functional.hub.service;

import io.metersphere.functional.hub.dto.DefaultHubCaseMapRow;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Serialize mirror writes with DETACH, then validate identity using current (not MVCC snapshot) reads. */
@Service
public class CaseAssetSourceBindingGuard {
    @Resource private JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAndValidate(String sourceProjectId, String sourceCaseId, String sourceRefId,
                                DefaultHubCaseMapRow observed, List<String> historical) {
        var targets = new TreeSet<>(historical);
        if (observed != null) targets.add(observed.getHubCaseId());
        // No old target can be overwritten on the create path. Keep its existing source-row
        // serialization/unique constraints instead of adding empty-range locks for new sources.
        if (targets.isEmpty()) return;
        // DETACH holds the same asset row until both binding deletion and the independent edit commit.
        // Lock assets before mapping rows to avoid reversing DETACH's asset -> mapping lock order.
        for (String target : targets) {
            jdbcTemplate.queryForList("SELECT id FROM functional_case WHERE id=? FOR UPDATE", target);
        }
        validateMapping(sourceCaseId, observed);
        var currentHistorical = jdbcTemplate.queryForList(
                "SELECT asset_case_id FROM case_asset_source_relation WHERE source_project_id=? AND source_case_id=? FOR UPDATE",
                String.class, sourceProjectId, sourceRefId);
        if (!new TreeSet<>(historical).equals(new TreeSet<>(currentHistorical))) {
            throw new CaseAssetBindingChangedException();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAndValidateMapping(DefaultHubCaseMapRow observed) {
        jdbcTemplate.queryForList("SELECT id FROM functional_case WHERE id=? FOR UPDATE", observed.getHubCaseId());
        validateMapping(observed.getBizCaseId(), observed);
    }

    private void validateMapping(String sourceCaseId, DefaultHubCaseMapRow observed) {
        var mappings = jdbcTemplate.queryForList(
                "SELECT id,hub_case_id FROM default_hub_case_map WHERE biz_case_id=? FOR UPDATE", sourceCaseId);
        boolean sameMap = observed == null ? mappings.isEmpty() : mappings.size() == 1
                && Objects.equals(observed.getId(), mappings.getFirst().get("id"))
                && Objects.equals(observed.getHubCaseId(), mappings.getFirst().get("hub_case_id"));
        if (!sameMap) throw new CaseAssetBindingChangedException();
    }
}
