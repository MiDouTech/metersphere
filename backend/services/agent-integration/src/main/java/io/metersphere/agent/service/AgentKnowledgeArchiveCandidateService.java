package io.metersphere.agent.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Independent derivative: candidate failure never changes archive or execution facts. No synthetic Agent token. */
@Service
public class AgentKnowledgeArchiveCandidateService {
    private final JdbcTemplate jdbc;private final AgentProtocolStore store;private final AgentProtocolAccess access;private final AgentBusinessAccess business;private final AgentSessionProtocolConfiguration config;
    public AgentKnowledgeArchiveCandidateService(JdbcTemplate jdbc,AgentProtocolStore store,AgentProtocolAccess access,AgentBusinessAccess business,AgentSessionProtocolConfiguration config){this.jdbc=jdbc;this.store=store;this.access=access;this.business=business;this.config=config;}
    @Scheduled(fixedDelay=30000,initialDelay=60000)
    public void drain(){
        String environment;try{config.requireKnowledge();environment=config.reference("pilot.environment-id");}catch(io.metersphere.sdk.exception.MSException disabled){return;}
        for(var archive:jdbc.queryForList("SELECT a.id,d.id AS document_id,a.project_id,a.user_id,a.source_type,a.source_id,a.run_id,a.payload,v.id AS version_id,v.content_snapshot,v.content_hash,v.source_version,d.original_name,d.sha256,p.organization_id FROM agent_execution_archive a JOIN ai_source_document d ON d.id=a.document_id AND d.deleted=0 JOIN project p ON p.id=a.project_id AND p.deleted=0 AND p.enable=1 JOIN test_asset_version v ON v.project_id=a.project_id AND v.asset_type='DOCUMENT' AND v.asset_id=d.id AND v.source_version=a.run_id WHERE a.state='DONE' AND p.organization_id=? AND NOT EXISTS (SELECT 1 FROM msp_protocol_migration m WHERE m.batch_id=CONCAT('archive:',a.id) AND m.state IN ('CANDIDATE','PUBLISHED','WITHDRAWN')) ORDER BY a.create_time DESC LIMIT 100",config.reference("pilot.organization-id"))){
            try{candidate(archive,environment);}catch(Exception failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Knowledge candidate stage=ARCHIVE_DERIVATION code=CANDIDATE_FAILED archiveId={} rootType={}",archive.get("id"),failure.getClass().getSimpleName());}
        }
    }
    Map<String,Object> candidate(Map<String,Object> archive,String environment){return store.transaction(()->{
        String project=(String)archive.get("project_id"),org=(String)archive.get("organization_id"),user=(String)archive.get("user_id"),version=(String)archive.get("version_id"),asset=(String)archive.get("document_id"),archiveId=(String)archive.get("id");
        String key=hash(List.of(org,project,asset,version));var prior=jdbc.queryForList("SELECT knowledge_id,state FROM msp_protocol_migration WHERE source_key=? FOR UPDATE",key);if(!prior.isEmpty())return prior.getFirst();
        config.requireKnowledge();access.lockUserAuthorization(user);require(access.member(user,org)&&business.userHasPermission(user,project,"FUNCTIONAL_CASE_AI:READ"),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        jdbc.queryForList("SELECT capability FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? FOR UPDATE",org,user,environment);
        require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND capability='KNOWLEDGE_EDIT' AND expires_at>?",Long.class,org,user,environment,System.currentTimeMillis())==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
        var source=jdbc.queryForList("SELECT content_snapshot,content_hash FROM test_asset_version WHERE id=? AND project_id=? AND asset_id=? AND asset_type='DOCUMENT' FOR UPDATE",version,project,asset);require(source.size()==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
        String raw=(String)source.getFirst().get("content_snapshot"),snapshotHash=(String)source.getFirst().get("content_hash");require(DigestUtils.sha256Hex(raw).equals(snapshotHash),"PROTOCOL_SNAPSHOT_HASH_MISMATCH");
        var provenance=decode((String)archive.get("payload"));String id=uuid();var content=new LinkedHashMap<String,Object>();content.put("schemaVersion","1.0");content.put("title",limited((String)archive.get("original_name"),config.number("limits.title-scalars"),config.number("limits.content-utf8-bytes")));content.put("type","OPERATION");content.put("body",limited(raw,config.number("limits.body-scalars"),config.number("limits.content-utf8-bytes")));content.put("readers",List.of(user));content.put("relations",List.of());
        content.put("sources",List.of(Map.of("projectId",project,"assetType","DOCUMENT","assetId",asset,"versionId",version,"snapshotHash",snapshotHash,"originalHash",Objects.toString(archive.get("sha256"),""),"contributes",true)));
        content.put("origin",Map.of("sourceType",archive.get("source_type"),"sourceId",archive.get("source_id"),"sourceVersion",archive.get("run_id"),"assetVersionId",version,"snapshotHash",snapshotHash,"originalHash",Objects.toString(archive.get("sha256"),""),"resultSource",Objects.toString(provenance.get("resultSource"),"UNKNOWN"),"validationMode",Objects.toString(provenance.get("validationMode"),"UNKNOWN")));
        limited(encode(content),Long.MAX_VALUE,config.number("limits.content-utf8-bytes"));long now=System.currentTimeMillis();jdbc.update("INSERT INTO msp_protocol_migration(source_key,batch_id,state,payload) VALUES (?,?,'PROCESSING',?)",key,"archive:"+archiveId,store.payload(Map.of("archiveId",archiveId,"versionId",version)));
        jdbc.update("INSERT INTO msp_knowledge(id,organization_id,creator_id,state,version,safety_generation,payload,update_time) VALUES (?,?,?,'CANDIDATE',1,1,?,?)",id,org,user,store.payload(content),now);
        jdbc.update("INSERT INTO msp_knowledge_version(knowledge_id,version,state,payload,content_hash,author_id,create_time) VALUES (?,1,'DRAFT',?,?,?,?)",id,store.payload(content),hash(content),user,now);jdbc.update("UPDATE msp_protocol_migration SET state='CANDIDATE',knowledge_id=? WHERE source_key=?",id,key);
        store.event("KNOWLEDGE:"+id,null,uuid(),"ARCHIVE_CANDIDATE_SAVED",user,Map.of("archiveId",archiveId,"sourceVersion",archive.get("run_id")));return Map.of("knowledgeId",id,"state","CANDIDATE");
    });}
}
