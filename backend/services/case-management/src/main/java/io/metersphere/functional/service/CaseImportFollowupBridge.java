package io.metersphere.functional.service;
import java.util.Map;
/** Optional integration adapter; the case module does not depend on Agent services. */
public interface CaseImportFollowupBridge {
 void prepareAssetImport(String jobId,String userId,Map<String,Object> decision);
}
