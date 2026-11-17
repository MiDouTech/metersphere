package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentSensitiveDataSanitizer;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.sdk.exception.MSException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Organization discovery with live contribution-source intersection, immutable published snapshots. */
@Service
public class AgentKnowledgeService {
    private final JdbcTemplate jdbc;
    private final AgentProtocolStore store;
    private final AgentProtocolAccess access;
    private final AgentBusinessAccess business;
    private final AgentSessionProtocolConfiguration config;
    private final TestAssetCatalogService catalog;
    public AgentKnowledgeService(JdbcTemplate jdbc,AgentProtocolStore store,AgentProtocolAccess access,
            AgentBusinessAccess business,AgentSessionProtocolConfiguration config,TestAssetCatalogService catalog) {
        this.jdbc=jdbc;this.store=store;this.access=access;this.business=business;this.config=config;this.catalog=catalog;
    }
    private AgentProtocolAccess.Actor actor(String org,boolean lock,boolean write) {
        var actor=access.knowledgeActor(org,lock);
        if(!actor.human())business.activeToken(write?AgentTokenScope.KNOWLEDGE_EDIT_V1:AgentTokenScope.KNOWLEDGE_READ_V1);
        return actor;
    }
    public Map<String,Object> search(String org,String keyword,int page,int size) {return search(org,keyword,page,size,Map.of());}
    public Map<String,Object> search(String org,String keyword,int page,int size,Map<String,String> filters) {
        var actor=actor(org,false,false);require(page>=1&&size>=1&&size<=100,"AGENT_VALIDATION_ERROR");
        var visible=new ArrayList<Map<String,Object>>();
        // Filter before totals/pagination; unauthorized records never influence visible counts.
        for(var row:jdbc.queryForList("SELECT * FROM msp_knowledge WHERE organization_id=? AND state IN ('PUBLISHED','DRAFT','CANDIDATE') ORDER BY update_time DESC,id",org)) {
            if(!actor.human()&&!"PUBLISHED".equals(row.get("state")))continue;
            try{var value=read(row,actor,null,actor.human());if(List.of("title","body","scope","system","module").stream().anyMatch(key->Objects.toString(value.get(key),"").toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))&&filters.entrySet().stream().allMatch(filter->filter.getValue()==null||filter.getValue().isBlank()||Objects.toString(value.get(filter.getKey()),"").contains(filter.getValue())))visible.add(value);}
            catch(MSException denied){if(!Set.of("AGENT_RESOURCE_NOT_ACCESSIBLE","PROTOCOL_KNOWLEDGE_UNAVAILABLE").contains(denied.getMessage()))throw denied;}
        }
        int start=(int)Math.min((long)(page-1)*size,visible.size());
        return Map.of("total",visible.size(),"records",visible.subList(start,Math.min(start+size,visible.size())));
    }
    public List<String> capabilities(String org){var actor=actor(org,false,false);return jdbc.queryForList("SELECT DISTINCT capability FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND capability IN ('KNOWLEDGE_EDIT','KNOWLEDGE_PUBLISH') AND expires_at>?",String.class,org,actor.user(),System.currentTimeMillis());}
    public Map<String,Object> get(String org,String id,Long version) {
        var actor=actor(org,false,false);return read(row(org,id,false),actor,version,true);
    }
    public Map<String,Object> history(String org,String id){
        var actor=actor(org,false,false);var row=row(org,id,false);read(row,actor,null,true);
        var versions=jdbc.queryForList("SELECT version,state,content_hash,create_time FROM msp_knowledge_version WHERE knowledge_id=? ORDER BY version DESC",id);
        if(!actor.human()||!actor.user().equals(row.get("creator_id")))versions=versions.stream().filter(v->"PUBLISHED".equals(v.get("state"))).toList();
        var usage=new ArrayList<Map<String,Object>>();
        for(var use:jdbc.queryForList("SELECT u.instance_id,u.knowledge_version,u.step_id,u.state,s.project_id,s.owner_id,s.environment_id FROM msp_knowledge_usage u JOIN msp_protocol_instance i ON i.id=u.instance_id JOIN msp_protocol_session s ON s.id=i.session_id WHERE u.knowledge_id=? AND s.organization_id=?",id,org)){
            try{access.project(actor,(String)use.get("project_id"),false);if(actor.user().equals(use.get("owner_id")))usage.add(Map.of("instanceId",use.get("instance_id"),"version",use.get("knowledge_version"),"stepId",use.get("step_id"),"state",use.get("state")));}catch(MSException denied){/* Do not expose inaccessible execution identifiers. */}
        }
        return Map.of("versions",versions,"usage",usage);
    }
    public Map<String,Object> fixed(String org,String id,long version,AgentProtocolAccess.Actor actor,boolean newAdoption) {
        var row=row(org,id,TransactionSynchronizationManager.isActualTransactionActive());
        if(newAdoption)require("PUBLISHED".equals(row.get("state")),"PROTOCOL_KNOWLEDGE_UNAVAILABLE");
        return read(row,actor,version,false);
    }
    private Map<String,Object> row(String org,String id,boolean lock) {
        var rows=jdbc.queryForList("SELECT * FROM msp_knowledge WHERE id=? AND organization_id=?"+(lock?" FOR UPDATE":""),id,org);
        if(rows.size()!=1)throw AgentBusinessAccess.denied();return rows.getFirst();
    }
    private Map<String,Object> read(Map<String,Object> row,AgentProtocolAccess.Actor actor,Long version,boolean drafts) {
        var content=store.data(row);long current=((Number)row.get("version")).longValue();
        if(version!=null) {
            var versions=jdbc.queryForList("SELECT payload,state FROM msp_knowledge_version WHERE knowledge_id=? AND version=?",row.get("id"),version);
            if(versions.size()!=1)throw AgentBusinessAccess.denied();
            require(!"REVOKED".equals(versions.getFirst().get("state"))||(drafts&&actor.human()),"PROTOCOL_KNOWLEDGE_UNAVAILABLE");
            require("PUBLISHED".equals(versions.getFirst().get("state"))||(drafts&&actor.human()&&("REVOKED".equals(versions.getFirst().get("state"))||actor.user().equals(row.get("creator_id")))),"PROTOCOL_KNOWLEDGE_UNAVAILABLE");
            content=store.data(versions.getFirst());current=version;
        } else if(!"PUBLISHED".equals(row.get("state"))||(drafts&&actor.human()&&actor.user().equals(row.get("creator_id"))&&current>((Number)row.get("published_version")).longValue())) {
            require(drafts && actor.user().equals(row.get("creator_id")),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        } else {
            long published=((Number)row.get("published_version")).longValue();
            content=store.data(jdbc.queryForMap("SELECT payload FROM msp_knowledge_version WHERE knowledge_id=? AND version=?",row.get("id"),published));current=published;
        }
        require("PUBLISHED".equals(row.get("state"))||version!=null||drafts,"PROTOCOL_KNOWLEDGE_UNAVAILABLE");
        checkReadable(content,actor,(String)row.get("creator_id"));
        var result=new LinkedHashMap<>(content);result.put("id",row.get("id"));result.put("version",current);
        result.put("expectedKnowledgeVersion",row.get("version"));result.put("state",row.get("state"));result.put("safetyGeneration",row.get("safety_generation"));
        if(version==null&&"PUBLISHED".equals(row.get("state"))&&row.get("published_version")!=null&&current>((Number)row.get("published_version")).longValue())result.put("state","DRAFT");
        result.put("adoptionCount",jdbc.queryForObject("SELECT COUNT(*) FROM msp_knowledge_usage WHERE knowledge_id=? AND state='OUTCOME_RECORDED'",Long.class,row.get("id")));result.put("updateTime",row.get("update_time"));
        result.put("lastVerifiedAt",jdbc.queryForObject("SELECT MAX(last_verified_at) FROM msp_knowledge_version WHERE knowledge_id=?",Long.class,row.get("id")));
        var safeSources=new ArrayList<Map<String,Object>>();
        for(var source:objects(content.getOrDefault("sources",List.of()))) {
            try{source(source,actor);safeSources.add(source);}catch(MSException unavailable){safeSources.add(Map.of("accessible",false));}
        }
        result.put("sources",safeSources);return result;
    }
    private void checkReadable(Map<String,Object> content,AgentProtocolAccess.Actor actor,String creator) {
        List<String> readers=strings(content.getOrDefault("readers",List.of(creator)));
        require(readers.contains(actor.user()),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        for(var source:objects(content.getOrDefault("sources",List.of())))
            if(!Boolean.FALSE.equals(source.get("contributes")))source(source,actor);
    }
    private void source(Map<String,Object> ref,AgentProtocolAccess.Actor actor) {
        String project=id(ref,"projectId"),type=text(ref,"assetType"),asset=id(ref,"assetId"),version=id(ref,"versionId");
        require(actor.organization().equals(access.organizationForProject(project)),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        String permission=switch(type){case "DOCUMENT"->"FUNCTIONAL_CASE_AI:READ";case "CASE"->"FUNCTIONAL_CASE:READ";case "DATASET"->"PROJECT_FILE_MANAGEMENT:READ";default->throw AgentBusinessAccess.denied();};
        require(business.userHasPermission(actor.user(),project,permission),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        // Delegate resource-state and individual asset visibility to the established reader.
        if(TransactionSynchronizationManager.isActualTransactionActive()){
            if("DOCUMENT".equals(type))jdbc.queryForList("SELECT id FROM ai_source_document WHERE id=? AND project_id=? FOR UPDATE",asset,project);
            else if("CASE".equals(type))jdbc.queryForList("SELECT id FROM functional_case WHERE id=? AND project_id=? FOR UPDATE",asset,project);
        }
        catalog.detail(project,type,asset);
        var rows=jdbc.queryForList("SELECT content_hash FROM test_asset_version WHERE id=? AND project_id=? AND asset_type=? AND asset_id=?"+(TransactionSynchronizationManager.isActualTransactionActive()?" FOR UPDATE":""),version,project,type,asset);
        require(rows.size()==1 && text(ref,"snapshotHash").equals(rows.getFirst().get("content_hash")),"AGENT_RESOURCE_NOT_ACCESSIBLE");
    }
    public Map<String,Object> save(String org,Map<String,Object> args) {
        fields(args,"knowledgeId","expectedKnowledgeVersion","content","environmentId");
        String resource=args.containsKey("knowledgeId")?id(args,"knowledgeId"):"CREATE:"+id(args,"idempotencyKey");
        return store.execute("knowledge.save",resource,args,lock -> authorizeRequest(org,args,lock,false),()->{},ctx -> {
            if(!ctx.actor().human())business.activeToken(AgentTokenScope.KNOWLEDGE_EDIT_V1);
            config.requireKnowledge();access.capability(ctx.actor(),id(args,"environmentId"),"KNOWLEDGE_EDIT",false);
            var content=object(args.get("content"));validate(content,ctx.actor(),false);
            String id;long version;String creator;
            if(args.containsKey("knowledgeId")) {
                id=id(args,"knowledgeId");var row=row(org,id,true);checkReadable(store.data(row),ctx.actor(),(String)row.get("creator_id"));
                var previous=store.data(row);
                require(strings(previous.get("readers")).containsAll(strings(content.getOrDefault("readers",List.of(ctx.actor().user())))),"AGENT_RESOURCE_NOT_ACCESSIBLE");
                for(var original:objects(previous.getOrDefault("sources",List.of())))if(!Boolean.FALSE.equals(original.get("contributes")))
                    require(objects(content.getOrDefault("sources",List.of())).stream().anyMatch(candidate -> original.equals(candidate)),"PROTOCOL_SOURCE_PROVENANCE_REQUIRED");
                require(number(args,"expectedKnowledgeVersion")==((Number)row.get("version")).longValue(),"VERSION_CONFLICT");
                creator=(String)row.get("creator_id");version=((Number)row.get("version")).longValue()+1;
                require(!Set.of("DELETED","REVOKED").contains(row.get("state")),"PROTOCOL_KNOWLEDGE_UNAVAILABLE");
                jdbc.update("UPDATE msp_knowledge SET version=?,state=CASE WHEN state='PUBLISHED' THEN state ELSE 'DRAFT' END,payload=?,update_time=? WHERE id=?",version,store.payload(content),System.currentTimeMillis(),id);
            } else {
                id=uuid();creator=ctx.actor().user();version=1;
                require(!content.containsKey("readers")||strings(content.get("readers")).equals(List.of(creator)),"AGENT_RESOURCE_NOT_ACCESSIBLE");
                content.put("readers",List.of(creator));
                jdbc.update("INSERT INTO msp_knowledge(id,organization_id,creator_id,state,version,safety_generation,payload,update_time) VALUES (?,?,?,'DRAFT',1,1,?,?)",id,org,creator,store.payload(content),System.currentTimeMillis());
            }
            jdbc.update("INSERT INTO msp_knowledge_version(knowledge_id,version,state,payload,content_hash,author_id,create_time) VALUES (?,?,'DRAFT',?,?,?,?)",id,version,store.payload(content),hash(content),ctx.actor().user(),System.currentTimeMillis());
            store.event("KNOWLEDGE:"+id,null,ctx.operationId(),"KNOWLEDGE_DRAFT",ctx.actor().user(),Map.of("version",version));
            return Map.of("knowledgeId",id,"version",version,"validationIssues",semanticIssues(content,new HashSet<>(),id,org,ctx.actor()));
        });
    }
    private AgentProtocolAccess.Actor authorizeRequest(String org,Map<String,Object> args,boolean lock,boolean write){
        var actor=actor(org,lock,write);
        if(args.containsKey("knowledgeId")){var existing=row(org,id(args,"knowledgeId"),lock);checkReadable(store.data(existing),actor,(String)existing.get("creator_id"));}
        else if(args.get("content") instanceof Map<?,?>)for(var source:objects(object(args.get("content")).getOrDefault("sources",List.of())))source(source,actor);
        return actor;
    }
    private void validate(Map<String,Object> content,AgentProtocolAccess.Actor actor,boolean publish) {
        fields(content,"schemaVersion","title","type","body","scope","preconditions","inputs","outputs","steps","successCriteria","relations","sources","readers","preparation","origin","system","module");
        for(String key:List.of("inputs","outputs"))if(content.containsKey(key))AgentProtocolTypes.definition(object(content.get(key)));
        if(content.containsKey("preparation")){var preparation=object(content.get("preparation"));fields(preparation,"query","create","verify","cleanup","credentialRefs","allowedActions");for(String key:List.of("query","create","verify","cleanup"))if(preparation.containsKey(key))preparation.put(key,limited(text(preparation,key),config.number("limits.body-scalars"),config.number("limits.content-utf8-bytes")));for(String reference:strings(preparation.getOrDefault("credentialRefs",List.of()))){id(Map.of("reference",reference),"reference");limited(reference,config.number("limits.reference-scalars"),config.number("limits.content-utf8-bytes"));}content.put("preparation",preparation);}
        require("1.0".equals(content.get("schemaVersion")),"PROTOCOL_VERSION_UNSUPPORTED");
        require(Set.of("RULE","NAVIGATION","OPERATION","DATA_PREPARATION","RECOVERY").contains(text(content,"type")),"AGENT_VALIDATION_ERROR");
        content.put("title",limited(text(content,"title"),config.number("limits.title-scalars"),config.number("limits.content-utf8-bytes")));
        if(content.containsKey("body"))content.put("body",limited(text(content,"body"),config.number("limits.body-scalars"),config.number("limits.content-utf8-bytes")));
        limited(encode(content),Long.MAX_VALUE,config.number("limits.content-utf8-bytes"));
        for(var ref:objects(content.getOrDefault("sources",List.of()))){for(String key:List.of("projectId","assetId","versionId"))limited(text(ref,key),config.number("limits.reference-scalars"),config.number("limits.content-utf8-bytes"));fields(ref,"projectId","assetType","assetId","versionId","snapshotHash","originalHash","contributes");source(ref,actor);}
        for(var relation:objects(content.getOrDefault("relations",List.of()))) {
            fields(relation,"knowledgeId","version","bindings");limited(id(relation,"knowledgeId"),config.number("limits.reference-scalars"),config.number("limits.content-utf8-bytes"));
            try{fixed(actor.organization(),id(relation,"knowledgeId"),number(relation,"version"),actor,false);}
            catch(MSException missing){if(!"AGENT_RESOURCE_NOT_ACCESSIBLE".equals(missing.getMessage()))throw missing;
                // Missing identifiers are semantic; existing but unreadable identifiers are forbidden.
                require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_knowledge WHERE id=?",Long.class,relation.get("knowledgeId"))==0,"AGENT_RESOURCE_NOT_ACCESSIBLE");}
        }
        if(publish){for(String key:List.of("body","scope","preconditions","successCriteria"))require(content.containsKey(key)&&!encode(content.get(key)).isBlank(),"PROTOCOL_KNOWLEDGE_INCOMPLETE");
            require(!AgentSensitiveDataSanitizer.containsSensitive(encode(content)),"PROTOCOL_SENSITIVE_CONTENT");}
    }
    private List<String> semanticIssues(Map<String,Object> content,Set<String> path,String id,String org,AgentProtocolAccess.Actor actor) {
        var issues=new ArrayList<String>();if(!path.add(id)){issues.add("RELATION_CYCLE");return issues;}
        for(String key:List.of("body","scope","preconditions","successCriteria"))if(!(content.get(key) instanceof String text)||text.isBlank())issues.add("MISSING_"+key.toUpperCase(Locale.ROOT));
        if("DATA_PREPARATION".equals(content.get("type"))){var preparation=object(content.getOrDefault("preparation",Map.of()));for(String key:List.of("query","create","verify","cleanup"))if(!(preparation.get(key) instanceof String text)||text.isBlank())issues.add("MISSING_PREPARATION_"+key.toUpperCase(Locale.ROOT));}
        for(var relation:objects(content.getOrDefault("relations",List.of())))try {
            String target=id(relation,"knowledgeId");var targetRow=row(org,target,false);
            var fixed=fixed(org,target,number(relation,"version"),actor,true);
            if(path.contains(target))issues.add("RELATION_CYCLE");else issues.addAll(semanticIssues(fixed,new HashSet<>(path),target,org,actor));
        }catch(MSException invalid){issues.add("RELATION_UNAVAILABLE");}
        return issues.stream().distinct().toList();
    }
    public Map<String,Object> govern(String org,String action,Map<String,Object> args) {
        fields(args,"knowledgeId","expectedKnowledgeVersion","environmentId","reason","readers","version");
        String id=id(args,"knowledgeId");
        return store.execute("knowledge."+action,id,args,lock -> authorizeRequest(org,args,lock,false),()->{},ctx -> {
            if(Set.of("publish","restore").contains(action))config.requireKnowledge();access.lockGrants(ctx.actor(),id(args,"environmentId"));
            access.capability(ctx.actor(),id(args,"environmentId"),"KNOWLEDGE_PUBLISH",true);
            var row=row(org,id,true);require(number(args,"expectedKnowledgeVersion")==((Number)row.get("version")).longValue(),"VERSION_CONFLICT");
            var content=store.data(row);checkReadable(content,ctx.actor(),(String)row.get("creator_id"));
            if("publish".equals(action)) {
                require(!"CANDIDATE".equals(row.get("state")),"PROTOCOL_CANDIDATE_REQUIRES_DRAFT");
                if(args.containsKey("readers"))content.put("readers",strings(args.get("readers")));
                validate(content,ctx.actor(),true);require(semanticIssues(content,new HashSet<>(),id,org,ctx.actor()).isEmpty(),"PROTOCOL_KNOWLEDGE_INCOMPLETE");
                // Reader enlargement cannot defeat any contributing project's current read authorization.
                for(String reader:strings(content.get("readers")))for(var source:objects(content.getOrDefault("sources",List.of())))if(!Boolean.FALSE.equals(source.get("contributes"))) {
                    String permission=switch(text(source,"assetType")){case "DOCUMENT"->"FUNCTIONAL_CASE_AI:READ";case "CASE"->"FUNCTIONAL_CASE:READ";default->"PROJECT_FILE_MANAGEMENT:READ";};
                    require(business.userHasPermission(reader,text(source,"projectId"),permission),"AGENT_RESOURCE_NOT_ACCESSIBLE");
                }
                long version=((Number)row.get("version")).longValue();
                require(jdbc.update("UPDATE msp_knowledge_version SET state='PUBLISHED',payload=?,content_hash=?,author_id=? WHERE knowledge_id=? AND version=? AND state='DRAFT'",store.payload(content),hash(content),ctx.actor().user(),id,version)==1,"VERSION_CONFLICT");
                jdbc.update("UPDATE msp_knowledge SET state='PUBLISHED',published_version=?,payload=?,update_time=? WHERE id=?",version,store.payload(content),System.currentTimeMillis(),id);
                jdbc.update("UPDATE msp_protocol_migration SET first_published_version=COALESCE(first_published_version,?),state='PUBLISHED' WHERE knowledge_id=? AND state IN ('CANDIDATE','PUBLISHED')",version,id);
            } else if(Set.of("disable","delete","revoke").contains(action)) {
                text(args,"reason");String state=Map.of("disable","DISABLED","delete","DELETED","revoke","REVOKED").get(action);
                jdbc.update("UPDATE msp_knowledge SET state=?,safety_generation=safety_generation+?,version=version+1,update_time=? WHERE id=?",state,"revoke".equals(action)?1:0,System.currentTimeMillis(),id);
                if("revoke".equals(action)) {
                    jdbc.update("UPDATE msp_knowledge_version SET state='REVOKED' WHERE knowledge_id=?",id);
                    AgentProtocolKnowledgeSafety.revoke(jdbc,store,org,id,ctx.operationId(),ctx.actor().user());
                }
            } else if("restore".equals(action)) {
                var version=jdbc.queryForMap("SELECT * FROM msp_knowledge_version WHERE knowledge_id=? AND version=?",id,number(args,"version"));
                require(!"REVOKED".equals(version.get("state")),"PROTOCOL_KNOWLEDGE_UNAVAILABLE");
                jdbc.update("UPDATE msp_knowledge SET state='DRAFT',version=version+1,payload=?,update_time=? WHERE id=?",version.get("payload"),System.currentTimeMillis(),id);
                jdbc.update("INSERT INTO msp_knowledge_version(knowledge_id,version,state,payload,content_hash,author_id,create_time) VALUES (?,?,'DRAFT',?,?,?,?)",id,((Number)row.get("version")).longValue()+1,version.get("payload"),version.get("content_hash"),ctx.actor().user(),System.currentTimeMillis());
            } else fail("AGENT_VALIDATION_ERROR");
            store.event("KNOWLEDGE:"+id,null,ctx.operationId(),"KNOWLEDGE_"+action.toUpperCase(Locale.ROOT),ctx.actor().user(),Map.of("reason",Objects.toString(args.get("reason"),"")));
            return Map.of("knowledgeId",id,"state",row(org,id,false).get("state"),"version",row(org,id,false).get("version"));
        });
    }
}
