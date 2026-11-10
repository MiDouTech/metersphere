package io.metersphere.functional.planreview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Current database permissions, shared by workers and HTTP; no cached role-name authorization. */
@Service
public class PlanReviewAuthorization {
    private static final Map<PlanReviewPolicy.Permission, String> RESOURCES = Map.ofEntries(
            Map.entry(PlanReviewPolicy.Permission.PLAN_READ, "PROJECT_TEST_PLAN:READ"),
            Map.entry(PlanReviewPolicy.Permission.PLAN_UPDATE, "PROJECT_TEST_PLAN:READ+UPDATE"),
            Map.entry(PlanReviewPolicy.Permission.PLAN_ADD, "PROJECT_TEST_PLAN:READ+ADD"),
            Map.entry(PlanReviewPolicy.Permission.PLAN_MEMBERS, "PROJECT_TEST_PLAN:READ+ASSOCIATION"),
            Map.entry(PlanReviewPolicy.Permission.REVIEW_READ, "CASE_REVIEW:READ"),
            Map.entry(PlanReviewPolicy.Permission.REVIEW_UPDATE, "CASE_REVIEW:READ+UPDATE"),
            Map.entry(PlanReviewPolicy.Permission.REVIEW_ADD, "CASE_REVIEW:READ+ADD"),
            Map.entry(PlanReviewPolicy.Permission.REVIEW_MEMBERS, "CASE_REVIEW:READ+RELEVANCE"),
            Map.entry(PlanReviewPolicy.Permission.REVIEW_OPINION, "CASE_REVIEW:READ+REVIEW"),
            Map.entry(PlanReviewPolicy.Permission.IMPORT, "FUNCTIONAL_CASE:READ+IMPORT"),
            Map.entry(PlanReviewPolicy.Permission.RESTORE_EDIT, "PLAN_REVIEW:RESTORE"),
            Map.entry(PlanReviewPolicy.Permission.GRANT_MANAGE, "PLAN_REVIEW:GRANT_MANAGE"),
            Map.entry(PlanReviewPolicy.Permission.NOTICE_RECOVER, "PLAN_REVIEW:NOTICE_RECOVER"),
            Map.entry(PlanReviewPolicy.Permission.TAKEOVER_APPROVE, "PLAN_REVIEW:TAKEOVER_APPROVE"),
            Map.entry(PlanReviewPolicy.Permission.LEGACY_MIGRATE, "PLAN_REVIEW:MIGRATE"));
    private final JdbcTemplate jdbc;
    private volatile String sharedLock;

    public PlanReviewAuthorization(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PlanReviewPolicy.Access current(String actor, String project, boolean write) {
        if (actor == null || actor.isBlank()) throw new PlanReviewFailure("UNAUTHENTICATED", 401, "请先登录");
        Long account = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND enable=1 AND deleted=0", Long.class, actor);
        Long target = jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=? AND deleted=0" + (write ? " AND enable=1" : ""), Long.class, project);
        if (account == null || account != 1 || target == null || target != 1) {
            throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE", 404, "项目或对象不可访问");
        }
        boolean locking=write && org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
        if(locking){
            // Canonical order: project service grant, then current account/project/role records.
            // Keep the existing project mutex order, without interpreting a grant flag.
            jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE",String.class,project);
            if(sharedLock==null)sharedLock=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)connection->connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")?" FOR SHARE":" FOR UPDATE");
            if(jdbc.queryForList("SELECT id FROM user WHERE id=? AND enable=1 AND deleted=0"+sharedLock,String.class,actor).size()!=1
                    || jdbc.queryForList("SELECT id FROM project WHERE id=? AND deleted=0 AND enable=1"+sharedLock,String.class,project).size()!=1)throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"项目或对象不可访问");
        }
        var resources = jdbc.queryForList("""
                SELECT CASE WHEN rel.source_id='system' AND role.type='SYSTEM' AND role.id='admin'
                  THEN '*' ELSE perm.permission_id END resource_id
                FROM user_role_relation rel JOIN user_role role ON role.id=rel.role_id AND role.enabled=1
                LEFT JOIN user_role_permission perm ON perm.role_id=role.id
                WHERE rel.user_id=? AND ((rel.source_id='system' AND role.type='SYSTEM' AND role.id='admin')
                    OR (rel.source_id=? AND role.type='PROJECT'))
                """+(locking?sharedLock:""), String.class, actor, project);
        Set<PlanReviewPolicy.Permission> granted = EnumSet.noneOf(PlanReviewPolicy.Permission.class);
        boolean admin = resources.contains("*");
        for (var entry : RESOURCES.entrySet()) if (admin || resources.contains(entry.getValue())) granted.add(entry.getKey());
        if (granted.isEmpty()) throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE", 404, "项目或对象不可访问");
        return new PlanReviewPolicy.Access(granted, true);
    }

    public void require(PlanReviewPolicy.Action action, String actor, String project) {
        if (!PlanReviewPolicy.allowed(action, current(actor, project, true))) {
            throw new PlanReviewFailure("PERMISSION_REQUIRED", 403, "当前账号缺少本操作权限");
        }
    }

    public void requireRead(String actor, String project, boolean planSide) {
        var permission = planSide ? PlanReviewPolicy.Permission.PLAN_READ : PlanReviewPolicy.Permission.REVIEW_READ;
        if (!current(actor, project, false).permissions().contains(permission)) {
            throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE", 404, "项目或对象不可访问");
        }
    }
    public static java.util.List<String> resourceIds(PlanReviewPolicy.Access access){return access.permissions().stream().map(RESOURCES::get).filter(java.util.Objects::nonNull).sorted().toList();}
}
