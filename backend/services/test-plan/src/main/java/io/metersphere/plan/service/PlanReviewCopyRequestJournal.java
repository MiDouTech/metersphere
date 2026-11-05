package io.metersphere.plan.service;

import io.metersphere.functional.planreview.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Immutable whole-request identity; child operations retain their own independent progress. */
@Service
public class PlanReviewCopyRequestJournal {
    @jakarta.annotation.Resource private JdbcTemplate jdbc;
    @jakarta.annotation.Resource private PlanReviewAuthorization authorization;
    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public String prepare(String project,String actor,String key,String request){
        if(key==null||key.isBlank()||key.length()>128)throw new PlanReviewFailure("REQUEST_KEY_REQUIRED",400,"请保留有效复制请求标识");
        var access=authorization.current(actor,project,true);
        if(!access.hasAll(Set.of(PlanReviewPolicy.Permission.PLAN_READ,PlanReviewPolicy.Permission.PLAN_ADD)))throw new PlanReviewFailure("PERMISSION_REQUIRED",403,"缺少当前计划读取和创建权限");
        String hash=hash(request);
        var rows=jdbc.queryForList("SELECT * FROM plan_review_copy_request WHERE project_id=? AND request_key=? FOR UPDATE",project,key);
        if(!rows.isEmpty()){
            var row=rows.getFirst();
            if(!actor.equals(row.get("original_actor")))throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"复制请求不可访问");
            if(!hash.equals(row.get("input_hash")))throw new PlanReviewFailure("REQUEST_KEY_CONFLICT",409,"同一复制请求不能更改原范围、目标或评审设置");
            return (String)row.get("id");
        }
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO plan_review_copy_request(id,project_id,request_key,original_actor,input_hash,request_json,create_time) VALUES(?,?,?,?,?,?,?)",id,project,key,actor,hash,request,System.currentTimeMillis());
        return id;
    }
    static String hash(String input){
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
