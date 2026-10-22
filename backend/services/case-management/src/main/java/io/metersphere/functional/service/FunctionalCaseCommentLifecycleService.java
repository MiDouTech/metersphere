package io.metersphere.functional.service;

import io.metersphere.functional.dto.FunctionalCaseCommentDTO;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.CommentUserInfo;
import io.metersphere.system.mapper.BaseUserMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class FunctionalCaseCommentLifecycleService {
    @Resource private JdbcTemplate jdbc;
    @Resource private FunctionalCaseCommentAccess access;
    @Resource private FunctionalCaseCommentAudit audit;
    @Resource private BaseUserMapper users;
    private static final BeanPropertyRowMapper<FunctionalCaseCommentDTO> ROW = BeanPropertyRowMapper.newInstance(FunctionalCaseCommentDTO.class);
    public record CommentPage(long total, int current, int pageSize, List<FunctionalCaseCommentDTO> list) { }
    public record DeletedComment(String id, String caseId, long updateTime, boolean deleted) { }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CommentPage page(String caseId, String userId, String parentId, int current, int pageSize) {
        var owner = access.requireCase(caseId, userId, false);
        if (current < 1 || pageSize < 1 || pageSize > 100) throw FunctionalCaseCommentAccess.invalid("评论分页参数无效，每页最多 100 条");
        boolean roots = StringUtils.isBlank(parentId);
        if (!roots && !Integer.valueOf(1).equals(jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_comment WHERE id=? AND case_id=? AND (parent_id IS NULL OR parent_id='')", Integer.class, parentId, caseId)))
            throw FunctionalCaseCommentAccess.denied();
        String condition = roots ? "case_id=? AND (parent_id IS NULL OR parent_id='')" : "case_id=? AND parent_id=?";
        List<Object> args = new ArrayList<>(); args.add(caseId); if (!roots) args.add(parentId);
        long total = Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_comment WHERE " + condition, Long.class, args.toArray()));
        int actual = (int) Math.min(current, Math.max(1L, (total + pageSize - 1) / pageSize));
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(pageSize); pageArgs.add((actual - 1L) * pageSize);
        List<FunctionalCaseCommentDTO> rows = jdbc.query("SELECT * FROM functional_case_comment WHERE " + condition + " ORDER BY create_time DESC,id DESC LIMIT ? OFFSET ?", ROW, pageArgs.toArray());
        List<FunctionalCaseCommentDTO> all = new ArrayList<>(rows);
        if (roots && !rows.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
            List<Object> childArgs = new ArrayList<>(); childArgs.add(caseId); rows.forEach(row -> childArgs.add(row.getId()));
            Map<String, Long> totals = new HashMap<>();
            jdbc.query("SELECT parent_id,COUNT(*) AS total FROM functional_case_comment WHERE case_id=? AND parent_id IN (" + placeholders + ") GROUP BY parent_id",
                    rs -> { totals.put(rs.getString("parent_id"), rs.getLong("total")); }, childArgs.toArray());
            List<FunctionalCaseCommentDTO> children = jdbc.query("SELECT * FROM (SELECT c.*,ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY create_time DESC,id DESC) AS rn FROM functional_case_comment c WHERE case_id=? AND parent_id IN (" + placeholders + ")) ranked WHERE rn<=10 ORDER BY create_time DESC,id DESC", ROW, childArgs.toArray());
            Map<String, List<FunctionalCaseCommentDTO>> byParent = new HashMap<>();
            children.forEach(child -> byParent.computeIfAbsent(child.getParentId(), ignored -> new ArrayList<>()).add(child));
            rows.forEach(row -> {
                row.setChildTotal(totals.getOrDefault(row.getId(), 0L));
                row.setChildComments(byParent.getOrDefault(row.getId(), List.of()));
            });
            all.addAll(children);
        }
        enrich(all, userId, owner.getProjectId());
        return new CommentPage(total, actual, pageSize, rows);
    }

    /** Old array clients either receive every row or an explicit upgrade message, never silent truncation. */
    @Transactional(readOnly = true)
    public List<FunctionalCaseCommentDTO> legacyList(String caseId, String userId) {
        var owner = access.requireCase(caseId, userId, false);
        List<FunctionalCaseCommentDTO> all = jdbc.query("SELECT * FROM functional_case_comment WHERE case_id=? ORDER BY create_time DESC,id DESC LIMIT 101", ROW, caseId);
        if (all.size() > 100) throw FunctionalCaseCommentAccess.invalid("评论较多，请刷新页面或升级客户端，使用评论分页接口");
        enrich(all, userId, owner.getProjectId());
        List<FunctionalCaseCommentDTO> roots = all.stream().filter(row -> StringUtils.isBlank(row.getParentId())).toList();
        for (var root : roots) {
            var replies = all.stream().filter(row -> Objects.equals(row.getParentId(), root.getId())).toList();
            root.setChildComments(replies); root.setChildTotal(replies.size());
        }
        return roots;
    }

    public void requireDeletePermission(String caseId, String commentId, String userId) {
        var owner = access.requireCase(caseId, userId, true);
        var authors = jdbc.queryForList("SELECT create_user FROM functional_case_comment WHERE id=? AND case_id=?", String.class, commentId, caseId);
        if (authors.size() != 1 || (!Objects.equals(authors.getFirst(), userId) && !access.mayModerate(userId, owner.getProjectId())))
            throw FunctionalCaseCommentAccess.denied();
    }

    @Transactional(rollbackFor = Exception.class)
    public DeletedComment delete(String caseId, String commentId, String userId, Long expectedUpdateTime) {
        requireDeletePermission(caseId, commentId, userId);
        if (expectedUpdateTime == null || expectedUpdateTime < 0) throw FunctionalCaseCommentAccess.invalid("请刷新评论并携带版本后重试");
        var rows = jdbc.query("SELECT * FROM functional_case_comment WHERE id=? AND case_id=? FOR UPDATE", ROW, commentId, caseId);
        if (rows.size() != 1) throw FunctionalCaseCommentAccess.denied();
        var row = rows.getFirst();
        if (!Objects.equals(row.getUpdateTime(), expectedUpdateTime)) throw FunctionalCaseCommentAccess.conflict();
        if (Boolean.TRUE.equals(row.getDeleted())) return new DeletedComment(commentId, caseId, row.getUpdateTime(), true);
        long version = Math.max(System.currentTimeMillis(), row.getUpdateTime() + 1);
        if (jdbc.update("UPDATE functional_case_comment SET content='',notifier='',deleted=1,deleted_at=?,deleted_by=?,update_time=? WHERE id=? AND update_time=? AND deleted=0",
                version, userId, version, commentId, expectedUpdateTime) != 1) throw FunctionalCaseCommentAccess.conflict();
        audit.record(commentId, caseId, userId, "DELETE", version, row.getContent(), "");
        return new DeletedComment(commentId, caseId, version, true);
    }

    private void enrich(List<FunctionalCaseCommentDTO> rows, String userId, String projectId) {
        Set<String> userIds = new LinkedHashSet<>();
        for (var row : rows) {
            if (StringUtils.isNotBlank(row.getCreateUser())) userIds.add(row.getCreateUser());
            if (StringUtils.isNotBlank(row.getReplyUser())) userIds.add(row.getReplyUser());
            if (StringUtils.isNotBlank(row.getNotifier())) userIds.addAll(Arrays.asList(row.getNotifier().split(";")));
        }
        Map<String, CommentUserInfo> directory = new HashMap<>();
        if (!userIds.isEmpty()) users.getCommentUserInfoByIds(new ArrayList<>(userIds)).forEach(user -> directory.put(user.getId(), user));
        boolean mayComment = access.hasPermission(userId, projectId, PermissionConstants.FUNCTIONAL_CASE_READ_COMMENT);
        boolean moderate = mayComment && access.mayModerate(userId, projectId);
        for (var row : rows) {
            Set<String> related = new LinkedHashSet<>();
            related.add(row.getCreateUser()); related.add(row.getReplyUser());
            if (StringUtils.isNotBlank(row.getNotifier())) related.addAll(Arrays.asList(row.getNotifier().split(";")));
            row.setCommentUserInfos(related.stream().map(directory::get).filter(Objects::nonNull).toList());
            if (row.getChildComments() == null) row.setChildComments(List.of());
            boolean active = !Boolean.TRUE.equals(row.getDeleted());
            if (!active) row.setContent("");
            row.setCanEdit(active && mayComment && Objects.equals(row.getCreateUser(), userId));
            row.setCanDelete(active && mayComment && (moderate || Objects.equals(row.getCreateUser(), userId)));
        }
    }
}
