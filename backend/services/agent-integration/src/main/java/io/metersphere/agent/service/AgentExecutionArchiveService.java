package io.metersphere.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.metersphere.agent.security.AgentSensitiveDataSanitizer;
import io.metersphere.functional.domain.AiSourceDocument;
import io.metersphere.functional.mapper.AiSourceDocumentMapper;
import io.metersphere.project.service.FileMetadataService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Independent, retryable derived assets. Never changes an execution's business verdict. */
@Service
public class AgentExecutionArchiveService {
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentBusinessAccess access;
    @Resource private FileMetadataService files;
    @Resource private AiSourceDocumentMapper documents;
    @Resource private TestAssetVersionService versions;
    @Resource private TestAssetGovernanceService governance;
    @Resource private PlatformTransactionManager transactions;
    private static final Set<String> TERMINAL=Set.of("SUCCESS","PARTIAL_SUCCESS","FAILED","CANCELED","EXPIRED");
    private static final ObjectMapper MAPPER=new ObjectMapper();

    public Map<String,Object> prepare(String type,String source,String user) {
        return prepare(type,source,user,null);
    }
    public Map<String,Object> prepare(String type,String source,String user,String expectedProject) {
        return new TransactionTemplate(transactions).execute(status->{
            var context=source(type,source,true);String project=(String)context.get("projectId");
            if(expectedProject!=null&&!expectedProject.equals(project))throw AgentBusinessAccess.denied();
            authorize(type,project,user);
            return enqueue(type,source,user,context);
        });
    }
    private Map<String,Object> enqueue(String type,String source,String user,Map<String,Object> context) {
        String run=(String)context.get("runId");
        var existing=jdbc.queryForList("SELECT id FROM agent_execution_archive WHERE source_type=? AND run_id=?",String.class,type,run);
        if(!existing.isEmpty())return status(existing.getFirst(),user);
        String payload=sanitize(JSON.toJSONString(context));
        if(payload.getBytes(StandardCharsets.UTF_8).length>5*1024*1024)throw new MSException("归档内容过大，请联系项目管理员处理");
        String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
        jdbc.update("INSERT INTO agent_execution_archive(id,source_type,source_id,run_id,project_id,user_id,request_hash,payload,next_attempt,create_time,update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)",id,type,source,run,context.get("projectId"),user,DigestUtils.sha256Hex(payload),payload,now,now,now);
        return status(id,user);
    }
    public Map<String,Object> status(String id,String user) {
        var rows=jdbc.queryForList("SELECT id,source_type AS sourceType,source_id AS sourceId,run_id AS runId,project_id AS projectId,state,document_id AS documentId,dataset_id AS datasetId,attempts,update_time AS updateTime FROM agent_execution_archive WHERE id=?",id);
        if(rows.size()!=1)throw AgentBusinessAccess.denied();var row=rows.getFirst();
        authorize((String)row.get("sourceType"),(String)row.get("projectId"),user);
        row.put("message","DONE".equals(row.get("state"))?"归档已写入项目文档；没有采集到可复用业务数据时不生成空数据资产":"归档独立处理，失败不会修改执行结果；可单独重试");
        return row;
    }
    public Map<String,Object> retry(String id,String user) {
        var state=status(id,user);
        if("FAILED".equals(state.get("state")))jdbc.update("UPDATE agent_execution_archive SET state='PENDING',attempts=0,next_attempt=?,update_time=? WHERE id=? AND state='FAILED'",System.currentTimeMillis(),System.currentTimeMillis(),id);
        return status(id,user);
    }
    public Map<String,Object> data(String id,String user,List<Map<String,Object>> records) {
        if(records==null||records.isEmpty()||records.size()>500)throw new MSException("请提供1到500条有来源的业务数据");
        for(var record:records)for(String key:List.of("sourceObjectId","reuseConditions","cleanupStatus"))
            if(!(record.get(key) instanceof String text)||text.isBlank()||text.length()>2000)throw new MSException("每条数据必须提供来源对象、复用条件和清理状态");
        String payload=sanitize(JSON.toJSONString(Map.of("resultSource","AGENT_REPORTED","records",records)));
        if(payload.getBytes(StandardCharsets.UTF_8).length>1024*1024)throw new MSException("业务数据归档不能超过1MiB");
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            var view=status(id,user);
            if(!access.userHasPermission(user,(String)view.get("projectId"),"PROJECT_FILE_MANAGEMENT:READ+ADD"))throw AgentBusinessAccess.denied();
            var rows=jdbc.queryForList("SELECT data_payload FROM agent_execution_archive WHERE id=? FOR UPDATE",id);
            Object previous=rows.getFirst().get("data_payload");
            if(previous!=null){if(!payload.equals(previous))throw new MSException("该运行已经归档了不同的数据，不能覆盖原事实");return;}
            jdbc.update("UPDATE agent_execution_archive SET data_payload=?,data_user_id=?,state='PENDING',attempts=0,next_attempt=?,update_time=? WHERE id=?",payload,user,System.currentTimeMillis(),System.currentTimeMillis(),id);
        });
        return status(id,user);
    }

    @Scheduled(fixedDelay=30000,initialDelay=60000)
    public void drain() {
        discover();
        var ids=jdbc.queryForList("SELECT id FROM agent_execution_archive WHERE state='PENDING' AND next_attempt<=? ORDER BY create_time LIMIT 10",String.class,System.currentTimeMillis());
        for(String id:ids)try {
            new TransactionTemplate(transactions).executeWithoutResult(status->publish(id));
        }catch(Exception failure){
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Execution archive {} failed",id,failure);
            jdbc.update("UPDATE agent_execution_archive SET attempts=attempts+1,state=IF(attempts>=5,'FAILED','PENDING'),next_attempt=?,update_time=? WHERE id=? AND state='PENDING'",System.currentTimeMillis()+60000,System.currentTimeMillis(),id);
        }
    }
    private void discover() {
        long since=jdbc.queryForObject("SELECT since_time FROM agent_execution_archive_baseline WHERE id=1",Long.class);
        var tasks=jdbc.queryForList("SELECT id,create_user AS owner FROM ai_execution_task t WHERE t.finished_at>=? AND t.current_execution_id IS NOT NULL AND t.status IN ('SUCCESS','PARTIAL_SUCCESS','FAILED','CANCELED','EXPIRED') AND NOT EXISTS (SELECT 1 FROM agent_execution_archive a WHERE a.source_type='TASK' AND BINARY a.run_id=BINARY t.current_execution_id) ORDER BY t.finished_at LIMIT 10",since);
        var submissions=jdbc.queryForList("SELECT id,user_id AS owner FROM agent_case_submission s WHERE s.create_time>=? AND s.gate_status='ACCEPTED' AND NOT EXISTS (SELECT 1 FROM agent_execution_archive a WHERE a.source_type='SUBMISSION' AND BINARY a.run_id=BINARY s.id) ORDER BY s.create_time LIMIT 10",since);
        discover("TASK",tasks);discover("SUBMISSION",submissions);
    }
    private void discover(String type,List<Map<String,Object>> sources) {
        for(var candidate:sources)try {
            new TransactionTemplate(transactions).executeWithoutResult(status->{
                String id=(String)candidate.get("id");var context=source(type,id,true);
                // The producer is durable; publishing independently rechecks current permissions.
                String run=(String)context.get("runId");
                if(jdbc.queryForObject("SELECT COUNT(*) FROM agent_execution_archive WHERE source_type=? AND run_id=?",Long.class,type,run)>0)return;
                String payload=sanitize(JSON.toJSONString(context));long now=System.currentTimeMillis();
                jdbc.update("INSERT INTO agent_execution_archive(id,source_type,source_id,run_id,project_id,user_id,request_hash,payload,next_attempt,create_time,update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),type,id,run,context.get("projectId"),candidate.get("owner"),DigestUtils.sha256Hex(payload),payload,now,now,now);
            });
        }catch(Exception failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Archive discovery failed for {}",candidate.get("id"),failure);}
    }
    void publish(String id) {
        var rows=jdbc.queryForList("SELECT * FROM agent_execution_archive WHERE id=? AND state='PENDING' FOR UPDATE SKIP LOCKED",id);
        if(rows.isEmpty())return;var row=rows.getFirst();
        String project=(String)row.get("project_id"),user=(String)row.get("user_id"),type=(String)row.get("source_type"),payload=(String)row.get("payload");
        authorize(type,project,user);
        if(!DigestUtils.sha256Hex(payload).equals(row.get("request_hash")))throw new MSException("归档内容校验失败");
        long now=System.currentTimeMillis();
        if(row.get("document_id")==null) {
        var section=Map.of("index",0,"title","执行来源与事实","start",0,"end",payload.length(),"text",payload);
        var parsed=Map.of("parserType","EXECUTION_ARCHIVE","summary","执行事实归档；Agent 声明不代表独立业务核验","text",payload,"sections",List.of(section));
        byte[] bytes=JSON.toJSONString(parsed).getBytes(StandardCharsets.UTF_8);
        String fileId=files.transferFileWithId("arc-"+id,"execution-"+id,"execution-"+id+".json",project,"NONE",user,bytes);
        var document=new AiSourceDocument();
        document.setId(id);document.setProjectId(project);document.setFileId(fileId);document.setOriginalName("执行理解与事实-"+row.get("run_id")+".json");
        document.setMimeType("application/json");document.setFileSize((long)bytes.length);document.setSha256(DigestUtils.sha256Hex(bytes));document.setDuplicate(false);
        document.setParseStatus("PARSED");document.setParserType("EXECUTION_ARCHIVE");document.setParsedResultPath(fileId);
        document.setSummary("仅整理来源执行的已记录事实；推断与待确认项单独保留，不把声明当成核验。");document.setSectionIndex(JSON.toJSONString(List.of(section)));
        document.setCreateUser(user);document.setCreateTime(now);document.setUpdateTime(now);document.setDeleted(false);documents.insert(document);
        versions.publish(project,"DOCUMENT",id,(String)row.get("run_id"),JSON.toJSONString(Map.of("document",document,"content",parsed)),user);
        governance.recordTrustedSource(project,"DOCUMENT",id,"AUTOMATION",type,(String)row.get("source_id"),"USER",user);
        versions.relate(project,"PRODUCES",type,(String)row.get("source_id"),null,"DOCUMENT",id,null,JSON.toJSONString(Map.of("runId",row.get("run_id"))),user);
        }
        String dataset=(String)row.get("dataset_id");
        if(row.get("data_payload")!=null&&dataset==null) {
            user=(String)row.get("data_user_id");
            authorize(type,project,user);
            if(!access.userHasPermission(user,project,"PROJECT_FILE_MANAGEMENT:READ+ADD"))throw AgentBusinessAccess.denied();
            String data=(String)row.get("data_payload");
            String reserved="arc-"+UUID.nameUUIDFromBytes((id+":dataset").getBytes(StandardCharsets.UTF_8));
            dataset=files.transferFileWithId(reserved,"execution-data-"+id,"execution-data-"+id+".json",project,"NONE",user,data.getBytes(StandardCharsets.UTF_8));
            versions.publish(project,"DATASET",dataset,(String)row.get("run_id"),data,user);
            governance.recordTrustedSource(project,"DATASET",dataset,"AUTOMATION",type,(String)row.get("source_id"),"USER",user);
            versions.relate(project,"PRODUCES",type,(String)row.get("source_id"),null,"DATASET",dataset,null,JSON.toJSONString(Map.of("runId",row.get("run_id"),"resultSource","AGENT_REPORTED")),user);
        }
        jdbc.update("UPDATE agent_execution_archive SET state='DONE',document_id=?,dataset_id=?,update_time=? WHERE id=?",id,dataset,now,id);
    }
    private Map<String,Object> source(String type,String id,boolean lock) {
        var result=new LinkedHashMap<String,Object>();
        result.put("sourceType",type);result.put("sourceId",id);result.put("inferences",List.of());
        result.put("pendingConfirmation",List.of("业务规则及推断需由使用者核对，不能仅凭结果状态认定业务已经核验"));
        result.put("businessData",Map.of("records",List.of(),"explanation","本执行协议未提交独立的可复用业务数据对象，不创建空数据集"));
        result.put("reuseConditions","仅适用于来源执行绑定的版本和范围，复用前重新核对当前配置与业务状态");
        result.put("cleanupStatus","未创建额外业务数据；归档文件按项目资产保留策略管理");
        if("TASK".equals(type)) {
            var rows=jdbc.queryForList("SELECT id,project_id,current_execution_id,name,objective,status,verdict FROM ai_execution_task WHERE id=?"+(lock?" FOR UPDATE":""),id);
            if(rows.size()!=1||!TERMINAL.contains(rows.getFirst().get("status"))||rows.getFirst().get("current_execution_id")==null)throw new MSException("请选择已结束且有执行记录的任务");
            var task=rows.getFirst();result.put("projectId",task.get("project_id"));result.put("runId",task.get("current_execution_id"));result.put("understanding",task);
            result.put("facts",jdbc.queryForList("SELECT id AS sourceObjectId,case_id AS caseId,case_name AS caseName,case_version AS caseVersion,status,result,writeback_status AS writebackStatus FROM ai_execution_case WHERE task_id=? ORDER BY pos,id",id));
        } else if("SUBMISSION".equals(type)) {
            var rows=jdbc.queryForList("SELECT id,project_id,case_id,content_hash,policy_hash,facts,result,gate_status,create_time FROM agent_case_submission WHERE id=?"+(lock?" FOR UPDATE":""),id);
            if(rows.size()!=1||!"ACCEPTED".equals(rows.getFirst().get("gate_status")))throw new MSException("请选择已提交的个人执行记录");
            var submission=rows.getFirst();
            try { submission.put("facts",MAPPER.readTree((String)submission.get("facts"))); }
            catch(Exception failure) { throw new MSException("来源执行事实格式无效，无法归档"); }
            result.put("projectId",submission.get("project_id"));result.put("runId",id);result.put("facts",List.of(submission));
            result.put("resultSource","AGENT_REPORTED");result.put("validationMode","STRUCTURE_ONLY_V1");
        } else throw new MSException("归档来源类型无效");
        return result;
    }
    private void authorize(String type,String project,String user) {
        if(!access.userHasPermission(user,project,"TASK".equals(type)?"AI_EXECUTION:READ":"FUNCTIONAL_CASE:READ"))throw AgentBusinessAccess.denied();
        Long active=jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=? AND deleted=0 AND enable=1",Long.class,project);
        if(active==null||active!=1)throw AgentBusinessAccess.denied();
    }
    static String sanitize(String json) {
        try {return MAPPER.writeValueAsString(redact(MAPPER.readTree(json)));}
        catch(Exception failure){throw new MSException("归档内容格式无效");}
    }
    private static JsonNode redact(JsonNode node) {
        if(node.isTextual())return MAPPER.getNodeFactory().textNode(AgentSensitiveDataSanitizer.sanitize(node.asText()).replaceAll("(?i)ms(?:at|rt)_[A-Za-z0-9_-]+","[REDACTED]"));
        if(node.isObject()) {
            var object=(ObjectNode)node;List<String> names=new ArrayList<>();object.fieldNames().forEachRemaining(names::add);
            for(String name:names) {
                var value=object.get(name);
                if(name.matches("(?i).*(password|passwd|secret|cookie|authorization|token|api.?key).*")&&!value.isNull())object.put(name,"[REDACTED]");
                else object.set(name,redact(value));
            }
        }else if(node.isArray()) {
            var array=(com.fasterxml.jackson.databind.node.ArrayNode)node;
            for(int index=0;index<array.size();index++)array.set(index,redact(array.get(index)));
        }
        return node;
    }
}
