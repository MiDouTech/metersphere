package io.metersphere.agent.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Request-identity migration: no persisted actor can grant future access. */
@Service
public class DataAssetLegacyAutoService {
 private final JdbcTemplate jdbc;
 private final GlobalAssetAccess global;
 private final AgentBusinessAccess access;
 private final DataAssetLegacyService legacy;
 public DataAssetLegacyAutoService(JdbcTemplate jdbc, GlobalAssetAccess global, AgentBusinessAccess access, DataAssetLegacyService legacy) {
  this.jdbc=jdbc;this.global=global;this.access=access;this.legacy=legacy;
 }
 private List<String> projects(String hint, GlobalAssetAccess.Scope scope) {
  var candidates=jdbc.queryForList("SELECT id FROM project WHERE organization_id=? AND deleted=0 AND enable=1 ORDER BY id",String.class,scope.organizationId());
  return candidates.stream().filter(p -> (hint==null || hint.isBlank() || hint.equals(p)) && allowedProject(p,scope)).toList();
 }
 private boolean denied(io.metersphere.sdk.exception.MSException e) {
  return "AGENT_RESOURCE_NOT_ACCESSIBLE".equals(e.getMessage());
 }
 private boolean allowedProject(String project, GlobalAssetAccess.Scope scope) {
  try {
   var candidate=global.context(project);
   return scope.organizationId().equals(candidate.organizationId()) && scope.actorId().equals(candidate.actorId()) && access.userHasPermission(scope.actorId(),project,"PROJECT_FILE_MANAGEMENT:READ");
  } catch(io.metersphere.sdk.exception.MSException e) {if(denied(e)) return false;throw e;}
 }
 private void discover(String project) {
  var families=jdbc.queryForList("SELECT DISTINCT COALESCE(NULLIF(ref_id,''),id) FROM file_metadata WHERE project_id=? AND UPPER(type) IN ('CSV','JSON','TXT','XLS','XLSX')",String.class,project);
  for(String family:families) {
   jdbc.update("INSERT IGNORE INTO data_asset_legacy_job(project_id,family_id,status,update_time) VALUES(?,?,'QUEUED',?)",project,family,System.currentTimeMillis());
   jdbc.update("UPDATE data_asset_legacy_job SET status='QUEUED',update_time=? WHERE project_id=? AND family_id=? AND status='DONE' AND EXISTS (SELECT 1 FROM file_metadata f WHERE f.project_id=? AND COALESCE(NULLIF(f.ref_id,''),f.id)=? AND NOT EXISTS (SELECT 1 FROM data_asset_source s WHERE CAST(s.source_file_id AS BINARY(256))=CAST(f.id AS BINARY(256))))",System.currentTimeMillis(),project,family,project,family);
  }
 }
 public Map<String,Object> status(String project) {
  var scope=global.authorize(project,"TEST_DATA_ASSET:READ");
  var allowed=projects(project,scope);
  boolean canRun;
  try {global.authorize(project,"TEST_DATA_ASSET:READ+UPDATE");canRun=true;} catch(io.metersphere.sdk.exception.MSException e){if(!denied(e))throw e;canRun=false;}
  var items=new ArrayList<Map<String,Object>>();
  for(var p:allowed) {
   // Discovery records identifiers only; GET never reads file bytes or imports versions.
   discover(p);
   items.addAll(jdbc.queryForList("SELECT project_id AS projectId,family_id AS familyId,status,error_code AS errorCode,trace_id AS traceId FROM data_asset_legacy_job WHERE project_id=? ORDER BY family_id",p));
  }
  var counts=new LinkedHashMap<String,Long>();
  counts.put("pending",items.stream().filter(i->"QUEUED".equals(i.get("status"))).count());
  counts.put("running",items.stream().filter(i->"RUNNING".equals(i.get("status"))).count());
  counts.put("failed",items.stream().filter(i->"FAILED".equals(i.get("status"))).count());
  counts.put("done",items.stream().filter(i->"DONE".equals(i.get("status"))).count());
  return Map.of("counts",counts,"canRun",canRun,"hasMore",counts.get("pending")+counts.get("running")>0,"items",items);
 }
 public Map<String,Object> run(String project, boolean retryFailed) {
  var scope=global.authorize(project,"TEST_DATA_ASSET:READ+UPDATE");
  for(var p:projects(project,scope)) {
   discover(p);
   var rows=jdbc.queryForList("SELECT family_id,status,update_time FROM data_asset_legacy_job WHERE project_id=? AND (status='QUEUED' OR (status='RUNNING' AND update_time<?)"+(retryFailed?" OR status='FAILED'":"")+") ORDER BY family_id LIMIT 1",p,System.currentTimeMillis()-300000);
   if(rows.isEmpty())continue;
   var job=rows.getFirst();String family=job.get("family_id").toString();long now=System.currentTimeMillis();
   int claimed=jdbc.update("UPDATE data_asset_legacy_job SET status='RUNNING',update_time=?,error_code=NULL WHERE project_id=? AND family_id=? AND status=? AND update_time=?",now,p,family,job.get("status"),job.get("update_time"));
   if(claimed==0)continue;
   try {
    legacy.importFile(p,family);
    jdbc.update("UPDATE data_asset_legacy_job SET status='DONE',update_time=? WHERE project_id=? AND family_id=? AND update_time=?",System.currentTimeMillis(),p,family,now);
   } catch(Exception e) {
    var safe=DataAssetErrors.map(e);String trace=UUID.randomUUID().toString();
    io.metersphere.agent.security.AgentSafeDiagnostics.failure(org.slf4j.LoggerFactory.getLogger(getClass()),"DATA_ASSET_LEGACY_AUTO_FAILURE","import",trace,safe.code(),e);
    jdbc.update("UPDATE data_asset_legacy_job SET status='FAILED',error_code=?,trace_id=?,update_time=? WHERE project_id=? AND family_id=? AND update_time=?",safe.code(),trace,System.currentTimeMillis(),p,family,now);
   }
   break;
  }
  return status(project);
 }
}
