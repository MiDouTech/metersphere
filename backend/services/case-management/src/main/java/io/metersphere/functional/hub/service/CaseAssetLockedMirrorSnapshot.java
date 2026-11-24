package io.metersphere.functional.hub.service;

import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseBlob;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Locking reads bypass both MyBatis first-level caches and an earlier MySQL repeatable-read snapshot. */
@Service
public class CaseAssetLockedMirrorSnapshot {
 @Resource private JdbcTemplate jdbc;
 public record Snapshot(FunctionalCase header,String contentHash) {}
 public Map<String,Snapshot> lockAndRead(List<String> requestedIds){
  var ids=requestedIds.stream().distinct().sorted().toList();
  String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
  var headers=jdbc.query("SELECT * FROM functional_case WHERE id IN ("+placeholders+") ORDER BY id FOR UPDATE",(rs,n)->{
   var c=new FunctionalCase();c.setId(rs.getString("id"));c.setProjectId(rs.getString("project_id"));c.setModuleId(rs.getString("module_id"));c.setRefId(rs.getString("ref_id"));
   c.setName(rs.getString("name"));c.setCaseEditType(rs.getString("case_edit_type"));String tags=rs.getString("tags");c.setTags(tags==null?null:JSON.parseArray(tags,String.class));
   c.setReviewStatus(rs.getString("review_status"));c.setLastExecuteResult(rs.getString("last_execute_result"));c.setExecuteUser(rs.getString("execute_user"));c.setLastExecuteUser(rs.getString("last_execute_user"));
   c.setLastExecuteTime(rs.getObject("last_execute_time")==null?null:rs.getLong("last_execute_time"));c.setCreateTime(rs.getObject("create_time")==null?null:rs.getLong("create_time"));c.setUpdateTime(rs.getObject("update_time")==null?null:rs.getLong("update_time"));
   c.setDeleted(rs.getBoolean("deleted"));c.setLatest(rs.getBoolean("latest"));return c;
  },ids.toArray());
  var blobs=new HashMap<String,FunctionalCaseBlob>();
  for(String id:ids){var found=jdbc.query("SELECT * FROM functional_case_blob WHERE id=? FOR UPDATE",(rs,n)->{
   var b=new FunctionalCaseBlob();b.setId(id);b.setSteps(rs.getBytes("steps"));b.setTextDescription(rs.getBytes("text_description"));b.setExpectedResult(rs.getBytes("expected_result"));b.setPrerequisite(rs.getBytes("prerequisite"));b.setDescription(rs.getBytes("description"));return b;
  },id);if(!found.isEmpty())blobs.put(id,found.getFirst());}
  var fields=readRows(ids,"SELECT field_id,value FROM functional_case_custom_field WHERE case_id=? ORDER BY field_id FOR UPDATE");
  var attachments=readRows(ids,"SELECT file_id,file_name FROM functional_case_attachment WHERE case_id=? ORDER BY id FOR UPDATE");
  var associations=readRows(ids,"SELECT file_id,deleted FROM file_association WHERE source_id=? ORDER BY id FOR UPDATE");
  var snapshots=new HashMap<String,Snapshot>();
  for(var h:headers)snapshots.put(h.getId(),new Snapshot(h,DefaultHubCaseSyncService.hashSnapshot(h,blobs.get(h.getId()),fields.get(h.getId()),attachments.get(h.getId()),associations.get(h.getId()))));
  return snapshots;
 }
 private Map<String,List<Map<String,Object>>> readRows(List<String> ids,String sql){var result=new HashMap<String,List<Map<String,Object>>>();for(String id:ids)result.put(id,jdbc.queryForList(sql,id));return result;}
}
