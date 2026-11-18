package io.metersphere.agent.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import org.slf4j.LoggerFactory;
import static io.metersphere.agent.service.AgentProtocolData.*;

@Service
public class AgentKnowledgeMigrationService {
    private final JdbcTemplate jdbc;private final AgentProtocolStore store;private final AgentProtocolAccess access;
    private final TestAssetCatalogService catalog;private final AgentKnowledgeService knowledge;private final AgentSessionProtocolConfiguration config;
    public AgentKnowledgeMigrationService(JdbcTemplate jdbc,AgentProtocolStore store,AgentProtocolAccess access,TestAssetCatalogService catalog,AgentKnowledgeService knowledge,AgentSessionProtocolConfiguration config){this.jdbc=jdbc;this.store=store;this.access=access;this.catalog=catalog;this.knowledge=knowledge;this.config=config;}
    public Map<String,Object> preview(String project,String asset,String version) {
        var document=catalog.detail(project,"DOCUMENT",asset);
        var rows=jdbc.queryForList("SELECT * FROM test_asset_version WHERE id=? AND project_id=? AND asset_type='DOCUMENT' AND asset_id=?",version,project,asset);require(rows.size()==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
        String raw=(String)rows.getFirst().get("content_snapshot");String computed=org.apache.commons.codec.digest.DigestUtils.sha256Hex(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        require(computed.equals(rows.getFirst().get("content_hash")),"PROTOCOL_SNAPSHOT_HASH_MISMATCH");
        var response=new LinkedHashMap<String,Object>();response.put("name",document.getName());response.put("snapshotHash",computed);response.put("sourceVersion",Objects.toString(rows.getFirst().get("source_version"),""));response.put("versionId",version);response.put("originalDownloadVerified",false);
        try {Object parsed=AgentProtocolData.JSON.readValue(raw,Object.class);response.put("content",parsed);response.put("parseStatus","PARSED");}
        catch(java.io.IOException malformed){response.put("content",raw);response.put("parseStatus","FAILED");response.put("code","SOURCE_PARSE_FAILED");}
        return response;
    }
    public Map<String,Object> migrate(String org,Map<String,Object> args) {
        fields(args,"batchId","environmentId","sources","cursor","previewOnly");String batch=id(args,"batchId");
        var actor=access.knowledgeActor(org,false);access.capability(actor,id(args,"environmentId"),"KNOWLEDGE_EDIT",false);
        var sources=objects(args.get("sources"));long cursor=number(args,"cursor");require(cursor<=sources.size(),"AGENT_VALIDATION_ERROR");
        boolean preview=!Boolean.FALSE.equals(args.get("previewOnly"));if(!preview){config.requireMigration();limited(encode(sources),Long.MAX_VALUE,config.number("limits.import-bytes"));}
        int limit=preview?1:Math.toIntExact(config.number("migration.batch-size"));var records=new ArrayList<Map<String,Object>>();
        for(int index=(int)cursor;index<Math.min((long)sources.size(),cursor+limit);index++) {
            var source=sources.get(index);String project=id(source,"projectId"),asset=id(source,"assetId"),version=id(source,"versionId");
            require(org.equals(access.organizationForProject(project)),"AGENT_RESOURCE_NOT_ACCESSIBLE");String sourceKey=hash(List.of(org,project,asset,version));
            try {
                var content=preview(project,asset,version);require("PARSED".equals(content.get("parseStatus")),"SOURCE_PARSE_FAILED");
                if(preview){records.add(Map.of("index",index,"state","PREVIEW","preview",content));continue;}
                Map<String,Object> result=store.transaction(()->{
                    jdbc.update("INSERT INTO msp_protocol_migration(source_key,batch_id,state,payload) VALUES (?,?,'PROCESSING',?) ON DUPLICATE KEY UPDATE source_key=source_key",sourceKey,batch,store.payload(source));
                    var row=jdbc.queryForMap("SELECT * FROM msp_protocol_migration WHERE source_key=? FOR UPDATE",sourceKey);
                    if("CANDIDATE".equals(row.get("state")))return Map.of("knowledgeId",row.get("knowledge_id"),"state","CANDIDATE","replayed",true);
                    var candidate=new LinkedHashMap<String,Object>();candidate.put("schemaVersion","1.0");candidate.put("title",content.get("name"));candidate.put("type","OPERATION");candidate.put("body",encode(content.get("content")));candidate.put("sources",List.of(Map.of("projectId",project,"assetType","DOCUMENT","assetId",asset,"versionId",version,"snapshotHash",content.get("snapshotHash"),"contributes",true)));candidate.put("origin",Map.of("sourceType","DOCUMENT","sourceId",asset,"sourceVersion",content.get("sourceVersion"),"assetVersionId",version,"snapshotHash",content.get("snapshotHash"),"resultSource","AGENT_REPORTED","validationMode","STRUCTURE_ONLY_V1","batchId",batch));
                    var saved=knowledge.save(org,Map.of("idempotencyKey","migration:"+sourceKey,"environmentId",id(args,"environmentId"),"content",candidate));
                    jdbc.update("UPDATE msp_knowledge SET state='CANDIDATE' WHERE id=?",saved.get("knowledgeId"));
                    jdbc.update("UPDATE msp_protocol_migration SET state='CANDIDATE',knowledge_id=?,error_code=NULL WHERE source_key=?",saved.get("knowledgeId"),sourceKey);return Map.of("knowledgeId",saved.get("knowledgeId"),"state","CANDIDATE");
                });records.add(result);
            }catch(RuntimeException failed){AgentProtocolDiagnostics.warn(LoggerFactory.getLogger(getClass()),"MIGRATION","SOURCE_MIGRATION_FAILED",sourceKey,failed);String code=failed instanceof io.metersphere.sdk.exception.MSException&&failed.getMessage()!=null&&failed.getMessage().matches("[A-Z][A-Z0-9_]{2,63}")?failed.getMessage():"SOURCE_MIGRATION_FAILED";if(!preview)store.transaction(()->{jdbc.update("INSERT INTO msp_protocol_migration(source_key,batch_id,state,error_code,payload) VALUES (?,?,'FAILED',?,?) ON DUPLICATE KEY UPDATE error_code=CASE WHEN state='FAILED' THEN VALUES(error_code) ELSE error_code END",sourceKey,batch,code,store.payload(source));return null;});records.add(Map.of("index",index,"state","FAILED","code",code));}
        }
        return Map.of("batchId",batch,"previewOnly",preview,"records",records,"nextCursor",Math.min((long)sources.size(),cursor+limit),"complete",cursor+limit>=sources.size());
    }
    public Map<String,Object> withdraw(String org,Map<String,Object> args){
        fields(args,"batchId","environmentId","reason");String batch=id(args,"batchId"),environment=id(args,"environmentId");var actor=access.knowledgeActor(org,false);access.capability(actor,environment,"KNOWLEDGE_PUBLISH",true);text(args,"reason");var results=new ArrayList<Map<String,Object>>();
        for(var row:jdbc.queryForList("SELECT m.*,k.version,k.published_version,k.state AS knowledge_state FROM msp_protocol_migration m JOIN msp_knowledge k ON k.id=m.knowledge_id WHERE m.batch_id=? AND k.organization_id=? ORDER BY m.source_key",batch,org)){
            if(row.get("first_published_version")==null||!row.get("first_published_version").equals(row.get("published_version"))||!"PUBLISHED".equals(row.get("knowledge_state"))){results.add(Map.of("knowledgeId",row.get("knowledge_id"),"state","SKIPPED_REPLACED_OR_UNPUBLISHED"));continue;}
            var request=Map.<String,Object>of("idempotencyKey","withdraw:"+batch+":"+row.get("source_key"),"knowledgeId",row.get("knowledge_id"),"expectedKnowledgeVersion",row.get("version"),"environmentId",environment,"reason",args.get("reason"));
            var result=knowledge.govern(org,"disable",request);store.transaction(()->{jdbc.update("UPDATE msp_protocol_migration SET state='WITHDRAWN' WHERE source_key=?",row.get("source_key"));return null;});results.add(result);
        }
        return Map.of("batchId",batch,"records",results,"originalsDeleted",false);
    }
}
