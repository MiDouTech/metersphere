package io.metersphere.agent.service;

import io.metersphere.agent.dto.CaseAssetBatchRequest;
import io.metersphere.agent.dto.TestAssetCategoryAssignRequest;
import io.metersphere.functional.asset.service.CaseAssetService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Durable, request-driven chunks retain the real user's current permission context. */
@Service
public class CaseAssetBatchService {
 private final JdbcTemplate jdbc; private final CaseAssetService assets;
 private final TestAssetGovernanceService governance; private final TransactionTemplate tx;
 public CaseAssetBatchService(JdbcTemplate jdbc,CaseAssetService assets,TestAssetGovernanceService governance,PlatformTransactionManager manager) {
  this.jdbc=jdbc;this.assets=assets;this.governance=governance;tx=new TransactionTemplate(manager);tx.setTimeout(25);
 }
 private String org(){String id=SessionUtils.getCurrentOrganizationId();if(id==null||id.isBlank())throw new MSException("请先选择组织");return id;}
 private void permission(String action) {
  String permission=switch(action){case "DELETE"->"CASE_ASSET:READ+DELETE";case "CATEGORY"->"TEST_ASSET_CATEGORY:ASSIGN";case "PRIORITY","TAGS"->"CASE_ASSET:READ+UPDATE";default->throw new MSException("批量操作类型无效");};
  SecurityUtils.getSubject().checkPermission("CASE_ASSET:READ");SecurityUtils.getSubject().checkPermission(permission);
 }
 private void validate(CaseAssetBatchRequest request) {
  permission(request.getAction());
  if(request.getIds()==null||request.getIds().isEmpty()||request.getIds().size()>100)throw new MSException("每次请选择 1～100 条资产用例");
  if("PRIORITY".equals(request.getAction())&&(request.getPriority()==null||request.getPriority().isBlank()))throw new MSException("请选择用例等级");
  if("TAGS".equals(request.getAction())&&(request.getTags()==null||!Set.of("ADD","REMOVE","REPLACE").contains(Objects.toString(request.getTagMode(),""))))throw new MSException("请选择标签处理方式");
  if(Set.of("PRIORITY","TAGS").contains(request.getAction())&&!Set.of("WRITE_BACK","DETACH").contains(Objects.toString(request.getSourceEditMode(),"")))throw new MSException("请选择来源资产处理方式");
 }
 public Map<String,Object> create(CaseAssetBatchRequest request) {
  validate(request);String user=SessionUtils.getUserId(),organization=org(),payload=JSON.toJSONString(request),hash=DigestUtils.sha256Hex(payload);
  String id=tx.execute(status->{
   jdbc.queryForList("SELECT id FROM organization WHERE id=? FOR UPDATE",organization);
   var previous=jdbc.queryForList("SELECT id,request_hash FROM case_asset_batch_job WHERE user_id=? AND organization_id=? AND request_id=?",user,organization,request.getRequestId());
   if(!previous.isEmpty()){if(!hash.equals(previous.getFirst().get("request_hash")))throw new MSException("同一请求标识不能用于不同批量操作");return (String)previous.getFirst().get("id");}
   String job=IDGenerator.nextStr();long now=System.currentTimeMillis();
   jdbc.update("INSERT INTO case_asset_batch_job(id,organization_id,catalog_id,user_id,request_id,request_hash,request_json,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?)",job,organization,request.getCatalogId(),user,request.getRequestId(),hash,payload,now,now);
   for(String caseId:new LinkedHashSet<>(request.getIds())){
    var detail=assets.detail(request.getCatalogId(),caseId);
    if(Boolean.TRUE.equals(detail.getDeleted()))throw new MSException("选择中包含已删除用例，请刷新");
    jdbc.update("INSERT INTO case_asset_batch_item(job_id,case_id,expected_update_time,state,update_time) VALUES(?,?,?,'PENDING',?)",job,caseId,detail.getUpdateTime(),now);
   }
   return job;
  });return get(id);
 }
 private Map<String,Object> owned(String id) {
  var rows=jdbc.queryForList("SELECT * FROM case_asset_batch_job WHERE id=? AND user_id=? AND organization_id=?",id,SessionUtils.getUserId(),org());
  if(rows.size()!=1)throw new MSException("批量任务不存在或当前账号无权访问");
  var row=rows.getFirst();permission(JSON.parseObject((String)row.get("request_json"),CaseAssetBatchRequest.class).getAction());return row;
 }
 public Map<String,Object> get(String id) {
  var row=owned(id);var items=jdbc.queryForList("SELECT case_id caseId,state,message FROM case_asset_batch_item WHERE job_id=? ORDER BY case_id",id);
  boolean pending=items.stream().anyMatch(i->"PENDING".equals(i.get("state"))),failed=items.stream().anyMatch(i->"FAILED".equals(i.get("state")));
  return Map.of("id",id,"catalogId",row.get("catalog_id"),"state",pending?"PENDING":failed?"PARTIAL_SUCCESS":"SUCCEEDED","items",items);
 }
 public Map<String,Object> advance(String id) {
  var row=owned(id);var request=JSON.parseObject((String)row.get("request_json"),CaseAssetBatchRequest.class);
  final String[] current={null};
  try {
   tx.executeWithoutResult(status->{
    jdbc.queryForList("SELECT id FROM case_asset_batch_job WHERE id=? FOR UPDATE",id);
    var pending=jdbc.queryForList("SELECT case_id,expected_update_time FROM case_asset_batch_item WHERE job_id=? AND state='PENDING' ORDER BY case_id LIMIT 1 FOR UPDATE",id);
    if(pending.isEmpty())return;
    var item=pending.getFirst();String caseId=(String)item.get("case_id");current[0]=caseId;
    jdbc.queryForList("SELECT id FROM functional_case WHERE id=? FOR UPDATE",caseId);
    var detail=assets.detail(request.getCatalogId(),caseId);
    if(Boolean.TRUE.equals(detail.getDeleted()))throw new MSException("用例已删除，请刷新后重新提交");
    if(!Objects.equals(((Number)item.get("expected_update_time")).longValue(),detail.getUpdateTime()))throw new MSException("用例已更新，请刷新后重新提交操作");
    switch(request.getAction()) {
     case "DELETE"->assets.deleteCase(request.getCatalogId(),caseId);
     case "CATEGORY"->{var assign=new TestAssetCategoryAssignRequest();assign.setProjectId(detail.getProjectId());assign.setCategoryId(request.getCategoryId());String ref=jdbc.queryForObject("SELECT COALESCE(NULLIF(ref_id,''),id) FROM functional_case WHERE id=?",String.class,caseId);governance.assign("CASE",ref,assign);}
     default->assets.batchEdit(request.getCatalogId(),caseId,detail.getUpdateTime(),request.getAction(),request.getPriority(),request.getTagMode(),request.getTags(),request.getSourceEditMode());
    }
    String message=Set.of("PRIORITY","TAGS").contains(request.getAction()) && "WRITE_BACK".equals(request.getSourceEditMode()) && detail.getSourceBindings()!=null && !detail.getSourceBindings().isEmpty()?"已写回源用例，等待资产持续同步":"处理成功";
    jdbc.update("UPDATE case_asset_batch_item SET state='SUCCEEDED',message=?,update_time=? WHERE job_id=? AND case_id=?",message,System.currentTimeMillis(),id,caseId);
    jdbc.update("UPDATE case_asset_batch_job SET update_time=? WHERE id=?",System.currentTimeMillis(),id);
   });
  } catch(Exception failure) {
   if(current[0]==null)throw failure;
   org.slf4j.LoggerFactory.getLogger(getClass()).warn("Asset batch item failed job={} case={}",id,current[0],failure);
   String message=failure instanceof MSException?failure.getMessage():"处理失败，请核对用例、权限和引用情况；排查编号 "+id;
   jdbc.update("UPDATE case_asset_batch_item SET state='FAILED',message=?,update_time=? WHERE job_id=? AND case_id=? AND state='PENDING'",org.apache.commons.lang3.StringUtils.left(message,950),System.currentTimeMillis(),id,current[0]);
  }
  return get(id);
 }
 public Map<String,Object> retry(String id) {
  owned(id);
  tx.executeWithoutResult(status->{
   jdbc.queryForList("SELECT id FROM case_asset_batch_job WHERE id=? FOR UPDATE",id);
   jdbc.update("UPDATE case_asset_batch_item SET state='PENDING',message=NULL,update_time=? WHERE job_id=? AND state='FAILED'",System.currentTimeMillis(),id);
  });return get(id);
 }
 public Map<String,Object> latest() {
  var ids=jdbc.queryForList("SELECT id FROM case_asset_batch_job WHERE user_id=? AND organization_id=? ORDER BY create_time DESC LIMIT 1",String.class,SessionUtils.getUserId(),org());
  return ids.isEmpty()?Map.of():get(ids.getFirst());
 }
}
