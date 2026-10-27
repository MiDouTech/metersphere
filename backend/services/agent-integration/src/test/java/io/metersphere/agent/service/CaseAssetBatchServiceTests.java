package io.metersphere.agent.service;
import io.metersphere.agent.dto.CaseAssetBatchRequest;
import io.metersphere.functional.asset.service.CaseAssetService;
import io.metersphere.sdk.exception.MSException;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CaseAssetBatchServiceTests {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 final CaseAssetService assets=mock(CaseAssetService.class);
 final TestAssetGovernanceService governance=mock(TestAssetGovernanceService.class);
 final CaseAssetBatchService service=new CaseAssetBatchService(jdbc,assets,governance,mock(PlatformTransactionManager.class));
 CaseAssetBatchRequest request(String action){var r=new CaseAssetBatchRequest();r.setAction(action);r.setIds(List.of("case"));r.setCatalogId("catalog");r.setRequestId("request");return r;}
 @Test void deletionPermissionCheckedBeforeFreezingTargets(){
  try(var security=mockStatic(SecurityUtils.class)){
   var subject=mock(Subject.class);security.when(SecurityUtils::getSubject).thenReturn(subject);
   doThrow(new org.apache.shiro.authz.AuthorizationException()).when(subject).checkPermission("CASE_ASSET:READ+DELETE");
   assertThrows(org.apache.shiro.authz.AuthorizationException.class,()->service.create(request("DELETE")));
   verifyNoInteractions(jdbc,assets,governance);
  }
 }
 @Test void sourceEditCannotBeSilentlyChosen(){
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getSubject).thenReturn(mock(Subject.class));
   var r=request("PRIORITY");r.setPriority("P1");
   assertThrows(MSException.class,()->service.create(r));
   verifyNoInteractions(jdbc,assets,governance);
  }
 }
 @Test void malformedTagModeProducesBusinessValidation(){
  try(var security=mockStatic(SecurityUtils.class)){
   security.when(SecurityUtils::getSubject).thenReturn(mock(Subject.class));
   var r=request("TAGS");r.setTags(List.of("tag"));r.setSourceEditMode("DETACH");
   assertThrows(MSException.class,()->service.create(r));
   verifyNoInteractions(jdbc,assets,governance);
  }
 }
}
