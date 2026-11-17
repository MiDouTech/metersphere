package io.metersphere.agent.service;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Formal writers hold the same knowledge rows as revocation until their business transaction commits. */
final class AgentProtocolKnowledgeSafety {
    private AgentProtocolKnowledgeSafety(){}
    static void requireSafe(JdbcTemplate jdbc,Map<String,Object> session,Map<String,Object> data){
        require(!Boolean.TRUE.equals(data.get("disputed"))&&!Boolean.TRUE.equals(data.get("upstreamDisputed"))&&!Boolean.TRUE.equals(data.get("needsRevalidation")),"PROTOCOL_CORRECTION_REQUIRED");
        for(var ref:objects(data.getOrDefault("knowledge",List.of())).stream().sorted(Comparator.comparing(value->id(value,"knowledgeId"))).toList()){
            var rows=jdbc.queryForList("SELECT state FROM msp_knowledge WHERE id=? AND organization_id=? FOR UPDATE",id(ref,"knowledgeId"),session.get("organization_id"));
            require(rows.size()==1&&!"REVOKED".equals(rows.getFirst().get("state")),"PROTOCOL_KNOWLEDGE_UNAVAILABLE");
            var versions=jdbc.queryForList("SELECT state FROM msp_knowledge_version WHERE knowledge_id=? AND version=? FOR UPDATE",ref.get("knowledgeId"),number(ref,"version"));
            require(versions.size()==1&&!"REVOKED".equals(versions.getFirst().get("state")),"PROTOCOL_KNOWLEDGE_UNAVAILABLE");
        }
    }
    static void revoke(JdbcTemplate jdbc,AgentProtocolStore store,String org,String knowledge,String operation,String actor){
        var rows=jdbc.queryForList("SELECT i.* FROM msp_protocol_instance i JOIN msp_protocol_session s ON s.id=i.session_id WHERE s.organization_id=? ORDER BY i.id FOR UPDATE",org);
        var contents=new LinkedHashMap<String,Map<String,Object>>();var affected=new HashSet<String>();
        for(var row:rows){String id=(String)row.get("id");var data=store.data(row);contents.put(id,data);
            boolean frozen=objects(data.getOrDefault("knowledge",List.of())).stream().anyMatch(ref->knowledge.equals(ref.get("knowledgeId")));
            boolean used=jdbc.queryForObject("SELECT COUNT(*) FROM msp_knowledge_usage WHERE instance_id=? AND knowledge_id=?",Long.class,id,knowledge)>0;
            if(frozen||used)affected.add(id);
        }
        boolean changed;
        do{changed=false;for(var row:rows){String id=(String)row.get("id");if(affected.contains(id))continue;
            var bindings=object(contents.get(id).getOrDefault("dependencies",Map.of()));boolean depends=bindings.values().stream().map(AgentProtocolData::object).anyMatch(dependency->affected.contains(dependency.get("instanceId")));
            if(!depends)for(var declaration:objects(object(contents.get(id).getOrDefault("contract",Map.of())).getOrDefault("dependencies",List.of()))){if(bindings.containsKey(declaration.get("alias")))continue;
                depends=rows.stream().filter(producer->affected.contains(producer.get("id"))&&Objects.equals(row.get("session_id"),producer.get("session_id"))&&Objects.equals(declaration.get("caseId"),producer.get("case_id"))).anyMatch(producer->{Object actual=object(contents.get(producer.get("id")).getOrDefault("contract",Map.of())).get("contractVersion");return actual instanceof Number version&&declaration.get("contractVersion") instanceof Number required&&version.longValue()==required.longValue();});if(depends)break;
            }
            if(depends){affected.add(id);contents.get(id).put("upstreamDisputed",true);changed=true;}
        }}while(changed);
        for(var row:rows){String id=(String)row.get("id");if(!affected.contains(id))continue;var data=contents.get(id);
            data.put("needsRevalidation",true);data.put("affectedAt",System.currentTimeMillis());if("ACCEPTED".equals(row.get("verification"))){data.put("disputed",true);data.put("correctionRequired",true);}
            var revoked=new HashSet<>(strings(data.getOrDefault("safetyRevocations",List.of())));revoked.add(knowledge);data.put("safetyRevocations",new ArrayList<>(revoked));
            jdbc.update("UPDATE msp_protocol_instance SET payload=?,phase=CASE WHEN phase='ENDED' THEN phase ELSE 'PAUSED' END,version=version+1 WHERE id=?",store.payload(data),id);
            store.event((String)row.get("session_id"),id,operation,"KNOWLEDGE_SAFETY_REVOKED",actor,Map.of("requiresRevalidation",true));
        }
    }
}
