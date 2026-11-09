package io.metersphere.functional.handoff;

import io.metersphere.functional.planreview.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class HandoffAuthorization {
    private final JdbcTemplate jdbc;
    private final PlanReviewAuthorization source;
    public HandoffAuthorization(JdbcTemplate jdbc, PlanReviewAuthorization source) { this.jdbc=jdbc; this.source=source; }
    public void read(String actor,String project) {
        source.requireRead(actor,project,true); source.requireRead(actor,project,false);
    }
    public void require(String actor,String project,String permission,boolean dualRead) {
        jdbc.update("INSERT IGNORE INTO plan_review_grant(project_id,update_time) VALUES(?,?)",project,System.currentTimeMillis());
        jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id=? FOR UPDATE",project);
        validateAccount(actor,project);
        if(dualRead) read(actor,project);
        var resources=jdbc.queryForList("""
            SELECT CASE WHEN r.source_id='system' AND role.type='SYSTEM' AND role.id='admin' THEN '*' ELSE p.permission_id END
            FROM user_role_relation r JOIN user_role role ON role.id=r.role_id AND role.enabled=1
            LEFT JOIN user_role_permission p ON p.role_id=role.id
            WHERE r.user_id=? AND ((r.source_id=? AND role.type='PROJECT') OR (r.source_id='system' AND role.type='SYSTEM' AND role.id='admin'))
            """+sharedLock(),String.class,actor,project);
        if(!resources.contains("*")&&!resources.contains("HIFOX_HANDOFF:"+permission)) throw fail("PERMISSION_REQUIRED",403,"缺少交接操作权限");
        if("PLAN_MANAGE".equals(permission)&&!source.current(actor,project,true).permissions().contains(PlanReviewPolicy.Permission.PLAN_UPDATE)) throw fail("PERMISSION_REQUIRED",403,"缺少测试计划更新权限");
    }
    private void validateAccount(String actor,String project){
        if(actor==null||actor.isBlank())throw fail("UNAUTHENTICATED",401,"请先登录");
        if(jdbc.queryForList("SELECT id FROM user WHERE id=? AND enable=1 AND deleted=0"+sharedLock(),String.class,actor).size()!=1||jdbc.queryForList("SELECT id FROM project WHERE id=? AND enable=1 AND deleted=0"+sharedLock(),String.class,project).size()!=1)throw fail("RESOURCE_NOT_ACCESSIBLE",404,"项目或账号不可访问");
    }
    private String sharedLock(){
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())return "";
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)connection->connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")?" FOR SHARE":" FOR UPDATE");
    }
    public boolean can(String actor,String project,String permission) {
        try { require(actor,project,permission,!"CONFIG_MANAGE".equals(permission));return true; }
        catch(PlanReviewFailure denied) {return false;}
    }
    static PlanReviewFailure fail(String code,int status,String message){return new PlanReviewFailure(code,status,message);}
}
