package io.metersphere.functional.hub.service;

import io.metersphere.functional.hub.dto.DefaultHubCaseMapRow;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

/** Reconcile only demonstrably unused, unedited duplicate mirrors; retain both physical assets/history. */
@Service
public class CaseAssetSourceMappingRepair {
 @Resource private JdbcTemplate jdbcTemplate;
 @Resource private io.metersphere.system.service.DefaultHubProjectService defaultHubProjectService;
 @Resource private CaseAssetLockedMirrorSnapshot lockedSnapshots;
 @Transactional(rollbackFor=Exception.class)
 public void repair(DefaultHubCaseMapRow map,String canonical,String sourceRef,String operator,String expectedModule) {
  String old=map.getHubCaseId();
  var snapshots=lockedSnapshots.lockAndRead(List.of(map.getBizCaseId(),old,canonical));
  var source=snapshots.get(map.getBizCaseId());var duplicate=snapshots.get(old);var current=snapshots.get(canonical);
  boolean valid=source!=null&&duplicate!=null&&current!=null;
  if(valid){
   var src=source.header();var a=duplicate.header();var b=current.header();
   valid=map.getBizProjectId().equals(src.getProjectId())&&sourceRef.equals(org.apache.commons.lang3.StringUtils.defaultIfBlank(src.getRefId(),src.getId()))
      &&Boolean.TRUE.equals(src.getLatest())&&!Boolean.TRUE.equals(src.getDeleted())
      &&source.contentHash().equals(map.getContentHash())&&duplicate.contentHash().equals(current.contentHash())
      &&java.util.Objects.equals(expectedModule,a.getModuleId())
      &&a.getProjectId().equals(defaultHubProjectService.getDefaultProjectId())&&a.getProjectId().equals(b.getProjectId())&&a.getModuleId().equals(b.getModuleId())
      &&!Boolean.TRUE.equals(a.getDeleted())&&!Boolean.TRUE.equals(b.getDeleted())
      &&a.getCreateTime()!=null&&a.getCreateTime().equals(a.getUpdateTime());
  }
  valid=valid&&count("SELECT COUNT(*) FROM case_asset_source_relation WHERE source_project_id=? AND source_case_id=? AND asset_case_id=?",map.getBizProjectId(),sourceRef,canonical)==1;
  if(valid){
   for(String sql:List.of(
    "SELECT COUNT(*) FROM case_asset_source_relation WHERE asset_case_id=?",
    "SELECT COUNT(*) FROM case_asset_lineage WHERE source_case_id=? OR target_case_id=?",
    "SELECT COUNT(*) FROM test_plan_functional_case WHERE functional_case_id=?",
    "SELECT COUNT(*) FROM case_review_functional_case WHERE case_id=?",
    "SELECT COUNT(*) FROM operation_log WHERE source_id=?",
    "SELECT COUNT(*) FROM functional_case_comment WHERE case_id=?",
    "SELECT COUNT(*) FROM case_review_functional_case_archive WHERE case_id=?",
    "SELECT COUNT(*) FROM test_asset_version WHERE asset_type='CASE' AND asset_id=?",
    "SELECT COUNT(*) FROM test_asset_relation WHERE (source_asset_type='CASE' AND source_asset_id=?) OR (target_asset_type='CASE' AND target_asset_id=?)",
    "SELECT COUNT(*) FROM functional_case WHERE ref_id=? AND id<>?",
    "SELECT COUNT(*) FROM case_asset_source_edit_audit WHERE asset_case_id=?")){
      Object[] args=sql.chars().filter(c->c=='?').count()==2?new Object[]{old,old}:new Object[]{old};
      if(count(sql,args)>0){valid=false;break;}
   }
  }
  valid=valid&&count("SELECT COUNT(*) FROM default_hub_case_map WHERE hub_case_id=? AND id<>?",old,map.getId())==0;
  if(!valid)throw new CaseAssetMappingConflictException(map.getBizCaseId(),old,canonical);
  int changed=jdbcTemplate.update("UPDATE default_hub_case_map SET hub_case_id=?,update_time=? WHERE id=? AND biz_case_id=? AND hub_case_id=?",canonical,System.currentTimeMillis(),map.getId(),map.getBizCaseId(),old);
  if(changed!=1)throw new MSException("CASE_SOURCE_MAPPING_CONFLICT：映射已变化，请重新同步");
  jdbcTemplate.update("INSERT INTO case_asset_source_edit_audit(id,asset_case_id,organization_id,operator_id,edit_mode,source_snapshot,create_time) VALUES(?,?,?,?,?,?,?)",IDGenerator.nextStr(),canonical,jdbcTemplate.queryForObject("SELECT organization_id FROM project WHERE id=?",String.class,map.getBizProjectId()),operator,"MAPPING_REPAIR",JSON.toJSONString(Map.of("sourceCaseId",map.getBizCaseId(),"sourceProjectId",map.getBizProjectId(),"previousAssetId",old,"canonicalAssetId",canonical)),System.currentTimeMillis());
 }
 /** Every caller only distinguishes 0, 1 and multiple. Current locking reads must not use an old MVCC snapshot. */
 private long count(String sql,Object...args){return jdbcTemplate.queryForList(sql.replace("SELECT COUNT(*)","SELECT 1")+" LIMIT 2 FOR UPDATE",args).size();}
}
