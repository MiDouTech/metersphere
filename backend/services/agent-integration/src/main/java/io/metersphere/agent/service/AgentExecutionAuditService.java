package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentSensitiveDataSanitizer;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionAuditService {
    @Resource private JdbcTemplate jdbc;

    @Transactional(rollbackFor=Exception.class)
    public String record(String actor,String action,String target,Object before,Object after,String traceId){
        String[] actorParts=split(actor,"SYSTEM");String[] targetParts=split(target,"UNKNOWN");
        String id=IDGenerator.nextStr();
        jdbc.update("INSERT INTO ai_execution_audit(id,actor_type,actor_id,action,target_type,target_id,before_json,after_json,trace_id,create_time) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id,actorParts[0],actorParts[1],StringUtils.abbreviate(StringUtils.defaultIfBlank(action,"UNKNOWN"),128),targetParts[0],targetParts[1],safe(before),safe(after),StringUtils.defaultIfBlank(traceId,IDGenerator.nextStr()),System.currentTimeMillis());
        return id;
    }
    private String safe(Object value){if(value==null)return null;return AgentSensitiveDataSanitizer.sanitize(JSON.toJSONString(value));}
    private String[] split(String value,String fallback){String normalized=StringUtils.defaultIfBlank(value,fallback+":unknown");int i=normalized.indexOf(':');return i<1?new String[]{fallback,normalized}:new String[]{normalized.substring(0,i).toUpperCase(),normalized.substring(i+1)};}
}
