package io.metersphere.functional.service;

import io.metersphere.functional.planreview.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class PlanReviewNoticeChannelAdapter implements PlanReviewNoticeChannel {
    private final JdbcTemplate jdbc;
    private final PlanReviewAuthorization authorization;
    public PlanReviewNoticeChannelAdapter(JdbcTemplate jdbc,PlanReviewAuthorization authorization){this.jdbc=jdbc;this.authorization=authorization;}
    public Set<String> recipients(Map<String,Object> relation,String reason){
        var result=new TreeSet<String>();
        for(String field:List.of("original_actor","source_actor")){Object actor=relation.get(field);if(actor instanceof String value&&!value.isBlank())result.add(value);}
        jdbc.queryForList("SELECT create_user FROM test_plan WHERE id=? AND project_id=?",String.class,relation.get("plan_id"),relation.get("project_id")).stream().filter(Objects::nonNull).forEach(result::add);
        jdbc.queryForList("SELECT create_user FROM case_review WHERE id=? AND project_id=?",String.class,relation.get("review_id"),relation.get("project_id")).stream().filter(Objects::nonNull).forEach(result::add);
        result.addAll(responsibles((String)relation.get("project_id"),reason));
        return result;
    }
    public Set<String> responsibles(String project,String reason){
        var result=new TreeSet<String>();
        // Only explicitly appointed project owners, never an arbitrary system administrator.
        result.addAll(jdbc.queryForList("SELECT rel.user_id FROM user_role_relation rel JOIN user_role role ON role.id=rel.role_id AND role.enabled=1 WHERE rel.source_id=? AND role.type='PROJECT' AND role.id='project_admin' FOR UPDATE",String.class,project));
        if("GRANT_REVOKED".equals(reason))result.addAll(jdbc.queryForList("SELECT rel.user_id FROM user_role_relation rel JOIN user_role role ON role.id=rel.role_id AND role.enabled=1 JOIN user_role_permission perm ON perm.role_id=role.id WHERE rel.source_id=? AND role.type='PROJECT' AND perm.permission_id='PLAN_REVIEW:GRANT_MANAGE' FOR UPDATE",String.class,project));
        return result;
    }
    public String eligibility(String project,String recipient){
        try{authorization.current(recipient,project,true);return "CURRENT";}
        catch(PlanReviewFailure denied){return "ACCOUNT_OR_PROJECT_ACCESS_UNAVAILABLE";}
    }
    public long publish(String recipient,String noticeId,long detectedAt,boolean recovered,boolean ended,String type){
        // Generic historical text remains safe after any later permission change. No object names,
        // opinion, IDs, project/organization metadata or navigable target are stored here.
        String summary="RECOVERY".equals(type)?"计划与评审关联的原业务故障已恢复。":"计划与评审关联有待处理事项。";
        summary+=(recovered?"该故障已恢复；":"")+(ended?"原关联已结束，不会恢复同步；":"")+"原识别时间："+java.time.Instant.ofEpochMilli(detectedAt)+"。请从有权访问的计划或评审详情查看通知记录。";
        jdbc.update("INSERT INTO notification(type,receiver,subject,status,create_time,operator,operation,resource_id,project_id,organization_id,resource_type,resource_name,content) VALUES('SYSTEM_NOTICE',?,?,'UNREAD',?,?,'PLAN_REVIEW_NOTICE','','','','TEST_PLAN','',?)",recipient,"计划评审关联通知",System.currentTimeMillis(),recipient,summary);
        Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        if(id==null||id<1)throw new IllegalStateException("Inbox receipt unavailable");
        return id;
    }
}
