package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AgentCaseSearchSnapshotService {
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentFunctionalCaseSearchService search;
    @Resource private AgentBusinessAccess access;
    public record SnapshotPage(String snapshotId, String projectId, long expiresAt, long total,
                               int current, int pageSize, boolean hasMore, List<AgentCaseDTO> cases) { }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public SnapshotPage create(AgentCaseSearchRequest input) {
        if (input.getTestPlanId() != null && !input.getTestPlanId().isBlank())
            throw new MSException("固定提取快照目前用于用例库，请使用计划读取接口读取计划上下文");
        var token = Objects.requireNonNull(AgentTokenContext.get());
        jdbc.queryForList("SELECT id FROM agent_token WHERE id=? FOR UPDATE", token.getId());
        long now = System.currentTimeMillis();
        Long active = jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_search_snapshot WHERE token_id=? AND expires_at>?", Long.class, token.getId(), now);
        if (active != null && active >= 5) throw new MSException("正在保留的提取快照较多，请释放已有快照或等待过期后重试");
        var request = JSON.parseObject(JSON.toJSONString(input), AgentCaseSearchRequest.class);
        request.setCurrent(1); request.setPageSize(100); request.setIncludeSteps(true);
        var first = search.search(request);
        if (first.getTotal() > 10000) throw new MSException("提取快照最多支持 10000 条，请缩小范围或使用文件导出");
        String id = UUID.randomUUID().toString();
        long expires = now + 15 * 60_000L;
        jdbc.update("INSERT INTO agent_case_search_snapshot(id,token_id,user_id,project_id,total,create_time,expires_at) VALUES (?,?,?,?,?,?,?)",
                id, token.getId(), token.getUserId(), first.getProjectId(), first.getTotal(), now, expires);
        long bytes = 0; int position = 0;
        var response = first;
        while (true) {
            for (AgentCaseDTO row : response.getCases()) {
                String payload = JSON.toJSONString(row);
                bytes += payload.getBytes(StandardCharsets.UTF_8).length;
                if (bytes > 50L * 1024 * 1024) throw new MSException("提取快照内容超过 50 MiB，请缩小范围或使用文件导出");
                jdbc.update("INSERT INTO agent_case_search_snapshot_item(snapshot_id,position,case_id,payload) VALUES (?,?,?,?)", id, position++, row.getCaseId(), payload);
            }
            if (!response.isHasMore()) break;
            request.setCurrent(request.getCurrent() + 1);
            response = search.search(request);
        }
        if (position != first.getTotal()) throw new MSException("提取范围发生变化，请重新创建快照");
        return page(id, 1, 10);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SnapshotPage page(String id, int current, int size) {
        if (current < 1 || size < 1 || size > 100) throw new MSException("分页参数无效，每页最多 100 条");
        var row = owned(id);
        String project = (String) row.get("project_id");
        access.project(project, "FUNCTIONAL_CASE:READ", false, AgentTokenScope.FUNCTIONAL_READ);
        Long invalid = jdbc.queryForObject("""
            SELECT COUNT(*) FROM agent_case_search_snapshot_item i LEFT JOIN functional_case c ON c.id=i.case_id
            WHERE i.snapshot_id=? AND (c.id IS NULL OR c.deleted=1 OR c.project_id<>?)
            """, Long.class, id, project);
        if (invalid != null && invalid > 0) throw new MSException("快照范围已失效，请重新创建快照");
        long total = ((Number) row.get("total")).longValue();
        int actual = (int) Math.min(current, Math.max(1, (total + size - 1) / size));
        var items = jdbc.query("SELECT payload FROM agent_case_search_snapshot_item WHERE snapshot_id=? ORDER BY position LIMIT ? OFFSET ?",
                (rs, index) -> JSON.parseObject(rs.getString(1), AgentCaseDTO.class), id, size, (actual - 1L) * size);
        return new SnapshotPage(id, project, ((Number) row.get("expires_at")).longValue(), total, actual, size, (long) actual * size < total, items);
    }

    @Transactional
    public void release(String id) {
        var row = owned(id);
        access.project((String) row.get("project_id"), "FUNCTIONAL_CASE:READ", false, AgentTokenScope.FUNCTIONAL_READ);
        jdbc.update("DELETE FROM agent_case_search_snapshot WHERE id=?", id);
    }

    private Map<String, Object> owned(String id) {
        var token = Objects.requireNonNull(AgentTokenContext.get());
        var rows = jdbc.queryForList("SELECT * FROM agent_case_search_snapshot WHERE id=? AND token_id=? AND user_id=? AND expires_at>?",
                id, token.getId(), token.getUserId(), System.currentTimeMillis());
        if (rows.size() != 1) throw new MSException("提取快照不存在、已过期或无权访问，请重新创建");
        return rows.getFirst();
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanup() { jdbc.update("DELETE FROM agent_case_search_snapshot WHERE expires_at<? LIMIT 100", System.currentTimeMillis()); }
}
