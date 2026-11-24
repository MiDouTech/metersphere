package io.metersphere.functional.hub.service;
import io.metersphere.sdk.exception.MSException;
/** Explicit allowlisted mapping identity; contains no source content. */
public class CaseAssetMappingConflictException extends MSException {
 private final String sourceCaseId,duplicateAssetId,canonicalAssetId;
 public CaseAssetMappingConflictException(String source,String duplicate,String canonical){
  super("CASE_SOURCE_MAPPING_CONFLICT：来源映射需人工核对，未合并或删除资产");
  sourceCaseId=source;duplicateAssetId=duplicate;canonicalAssetId=canonical;
 }
 public String diagnosticIds(){return " sourceCase="+safe(sourceCaseId)+" duplicateAsset="+safe(duplicateAssetId)+" canonicalAsset="+safe(canonicalAssetId);}
 private static String safe(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{1,64}")?id:"UNKNOWN";}
}
