package io.metersphere.system.service;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.utils.SessionUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Arrays;

/** Reserved configuration namespace; never a business project or a source authorization. */
@Service
public class PlatformNotificationScope {
    public static final String ID = "__MSP_PLATFORM_NOTIFICATIONS__";
    private final JdbcTemplate jdbc;

    public PlatformNotificationScope(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireAdmin() {
        requireAdmin(SessionUtils.getUserId());
    }

    public void requireAdmin(String actor) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM user u JOIN user_role_relation r ON r.user_id=u.id JOIN user_role role ON role.id=r.role_id WHERE u.id=? AND u.enable=1 AND u.deleted=0 AND r.source_id='system' AND role.type='SYSTEM' AND role.id='admin' AND role.enabled=1", Long.class, actor);
        if (count == null || count == 0) throw new UnauthorizedException("仅系统管理员可管理平台通知配置");
    }

    public void requireScope(String id) {
        requireAdmin();
        if (!ID.equals(id)) throw new MSException("通知配置不属于平台集合");
    }

    public void requireScope(String id, String actor) {
        requireAdmin(actor);
        if (!ID.equals(id)) throw new MSException("通知配置不属于平台集合");
    }

    public void requireSystemPermissions(String actor, String[] permissions, boolean all) {
        var granted=jdbc.queryForList("SELECT CASE WHEN role.id='admin' THEN '*' ELSE p.permission_id END FROM user u JOIN user_role_relation r ON r.user_id=u.id JOIN user_role role ON role.id=r.role_id LEFT JOIN user_role_permission p ON p.role_id=role.id WHERE u.id=? AND u.enable=1 AND u.deleted=0 AND r.source_id='system' AND role.type='SYSTEM' AND role.enabled=1",String.class,actor);
        boolean allowed=granted.contains("*") || (all ? Arrays.stream(permissions).allMatch(granted::contains) : Arrays.stream(permissions).anyMatch(granted::contains));
        if(!allowed) throw new UnauthorizedException("缺少对应的系统通知权限");
    }
}
