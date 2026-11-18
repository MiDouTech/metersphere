package io.metersphere.agent.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Explicit human bootstrap; never invent a natural-person link, grant or stable Agent identifier. */
@Service
public class AgentProtocolAdministrationService {
    public static final Set<String> CAPABILITIES=Set.of("EXECUTE","GOVERN","VERIFY_EXTERNAL","RESULT_REVIEW","CONFIRM_SUBSTITUTION","KNOWLEDGE_EDIT","KNOWLEDGE_PUBLISH","AUTHORIZE_TASK","BUDGET_READ","EVIDENCE_READ","EVIDENCE_WRITE");
    private final JdbcTemplate jdbc;private final AgentProtocolStore store;private final AgentProtocolAccess access;
    public AgentProtocolAdministrationService(JdbcTemplate jdbc,AgentProtocolStore store,AgentProtocolAccess access){this.jdbc=jdbc;this.store=store;this.access=access;}
    public Map<String,Object> inspect(String org){access.trustedAdministrator(org);return Map.of("identities",jdbc.queryForList("SELECT user_id,person_ref,version,evidence_ref FROM msp_protocol_identity WHERE organization_id=?",org),"subjects",jdbc.queryForList("SELECT token_id,user_id,agent_ref,enabled,version FROM msp_protocol_subject WHERE organization_id=?",org),"grants",jdbc.queryForList("SELECT user_id,environment_id,capability,expires_at,version FROM msp_protocol_grant WHERE organization_id=?",org));}
    public Map<String,Object> save(String org,String type,Map<String,Object> args) {
        return store.transaction(()->{
            access.trustedAdministrator(org);String user=id(args,"userId");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND enable=1 AND deleted=0",Long.class,user)==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
            require(access.member(user,org),"AGENT_RESOURCE_NOT_ACCESSIBLE");
            if("identity".equals(type)) {
                fields(args,"userId","personRef","evidenceRef","expectedVersion");
                var rows=jdbc.queryForList("SELECT version FROM msp_protocol_identity WHERE user_id=? AND organization_id=? FOR UPDATE",user,org);
                long version=rows.isEmpty()?0:((Number)rows.getFirst().get("version")).longValue();require(number(args,"expectedVersion")==version,"VERSION_CONFLICT");
                jdbc.update("INSERT INTO msp_protocol_identity(user_id,organization_id,person_ref,version,evidence_ref) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE person_ref=VALUES(person_ref),version=VALUES(version),evidence_ref=VALUES(evidence_ref)",user,org,id(args,"personRef"),version+1,text(args,"evidenceRef"));
            } else if("subject".equals(type)) {
                fields(args,"userId","tokenId","agentRef","enabled","expectedVersion");String token=id(args,"tokenId");
                require(jdbc.queryForObject("SELECT COUNT(*) FROM agent_token WHERE id=? AND user_id=?",Long.class,token,user)==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
                var rows=jdbc.queryForList("SELECT * FROM msp_protocol_subject WHERE token_id=? FOR UPDATE",token);long version=rows.isEmpty()?0:((Number)rows.getFirst().get("version")).longValue();
                require(rows.isEmpty()||org.equals(rows.getFirst().get("organization_id")),"AGENT_RESOURCE_NOT_ACCESSIBLE");require(number(args,"expectedVersion")==version&&args.get("enabled") instanceof Boolean,"VERSION_CONFLICT");
                jdbc.update("INSERT INTO msp_protocol_subject(token_id,organization_id,user_id,agent_ref,enabled,version) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE agent_ref=VALUES(agent_ref),enabled=VALUES(enabled),version=VALUES(version)",token,org,user,id(args,"agentRef"),args.get("enabled"),version+1);
            } else if("grant".equals(type)) {
                fields(args,"userId","environmentId","capability","expiresAt","expectedVersion");String env=id(args,"environmentId"),cap=text(args,"capability");require(CAPABILITIES.contains(cap),"AGENT_VALIDATION_ERROR");
                var rows=jdbc.queryForList("SELECT version FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND capability=? FOR UPDATE",org,user,env,cap);long version=rows.isEmpty()?0:((Number)rows.getFirst().get("version")).longValue();require(number(args,"expectedVersion")==version,"VERSION_CONFLICT");
                jdbc.update("INSERT INTO msp_protocol_grant(organization_id,user_id,environment_id,capability,expires_at,version) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at),version=VALUES(version)",org,user,env,cap,number(args,"expiresAt"),version+1);
            } else fail("AGENT_VALIDATION_ERROR");
            access.trustedAdministrator(org);store.event("ADMIN:"+org,null,uuid(),"PROTOCOL_"+type.toUpperCase(Locale.ROOT),io.metersphere.system.utils.SessionUtils.getUserId(),Map.of("userId",user));return Map.of("saved",true);
        });
    }
}
