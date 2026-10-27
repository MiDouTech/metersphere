package io.metersphere.agent.service;
import io.metersphere.agent.dto.ImportFollowupRequest;
import io.metersphere.functional.service.CaseImportFollowupBridge;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
import java.util.Map;
@Component
public class AssetImportFollowupBridge implements CaseImportFollowupBridge {
 private final ImportFollowupCoordinator coordinator;
 public AssetImportFollowupBridge(@Lazy ImportFollowupCoordinator coordinator){this.coordinator=coordinator;}
 public void prepareAssetImport(String id,String user,Map<String,Object> decision){
  ImportFollowupRequest request;
  try{request=JSON.parseObject(JSON.toJSONString(decision),ImportFollowupRequest.class);}catch(Exception invalid){throw new MSException("导入关联参数无效");}
  coordinator.prepare(id,user,request);
 }
}
