package io.metersphere.bug.service;

import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.domain.BugExample;
import io.metersphere.bug.mapper.BugMapper;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Maintains the normalized, searchable representation of a bug's current handlers.
 * The legacy {@code bug.handle_user} column remains the compatibility/display value.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class BugHandleUserRelationService {

    private static final String INSERT_SQL = """
            INSERT INTO bug_handle_user_relation
                (bug_id, project_id, platform, handle_user_id, create_time)
            VALUES (?, ?, ?, ?, ?)
            """;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private BugMapper bugMapper;

    public List<String> parse(String raw) {
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        String normalized = raw.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .map(value -> StringUtils.removeStart(StringUtils.removeEnd(value, "\""), "\""))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new), List::copyOf));
    }

    public String normalize(String raw) {
        return String.join(",", parse(raw));
    }

    public void replace(Bug bug) {
        if (bug == null || StringUtils.isBlank(bug.getId())) {
            return;
        }
        deleteByBugIds(List.of(bug.getId()));
        List<String> handlerIds = parse(bug.getHandleUser());
        if (handlerIds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        jdbcTemplate.batchUpdate(INSERT_SQL, handlerIds, handlerIds.size(), (statement, handlerId) -> {
            statement.setString(1, bug.getId());
            statement.setString(2, bug.getProjectId());
            statement.setString(3, StringUtils.defaultString(bug.getPlatform()));
            statement.setString(4, handlerId);
            statement.setLong(5, now);
        });
    }

    public void replaceAll(Collection<Bug> bugs) {
        if (CollectionUtils.isEmpty(bugs)) {
            return;
        }
        bugs.forEach(this::replace);
    }

    public void deleteByBugIds(Collection<String> bugIds) {
        if (CollectionUtils.isEmpty(bugIds)) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(bugIds.size(), "?"));
        jdbcTemplate.update("DELETE FROM bug_handle_user_relation WHERE bug_id IN (" + placeholders + ")", bugIds.toArray());
    }

    public void rebuildProject(String projectId, String platform) {
        BugExample example = new BugExample();
        BugExample.Criteria criteria = example.createCriteria().andProjectIdEqualTo(projectId);
        if (StringUtils.isNotBlank(platform)) {
            criteria.andPlatformEqualTo(platform);
        }
        replaceAll(bugMapper.selectByExample(example));
    }
}
