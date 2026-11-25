package io.metersphere.functional.asset.service;
import io.metersphere.sdk.util.LogUtils;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CaseAssetFailureDiagnosticsTests {
 @Test void staleBindingHasStableRetryableClassification(){
  try(var log=mockStatic(LogUtils.class)){
   String display=CaseAssetFailureDiagnostics.record("CASE_SYNC","effect-id",new io.metersphere.functional.hub.service.CaseAssetBindingChangedException());
   assertTrue(display.contains("CASE_SOURCE_BINDING_CHANGED"));assertTrue(display.contains("effect-id"));
   log.verify(()->LogUtils.error(argThat((Object line)->line.toString().contains("category=CASE_SOURCE_BINDING_CHANGED")&&line.toString().contains("reference=effect-id"))));
  }
 }
 @Test void injectedDatabaseFailureKeepsSqlIdentityButNeverPrivateMessageOrStack(){
  var failure=new DataAccessResourceFailureException("secret-password",new SQLException("Authorization Bearer private", "23000",1062));
  try(var log=mockStatic(LogUtils.class)){
   String display=CaseAssetFailureDiagnostics.record("HISTORY_SYNC","item:case",failure);
   assertTrue(display.contains("item:case"));assertFalse(display.contains("secret-password"));
   log.verify(()->LogUtils.error(argThat((Object line)->line.toString().contains("sqlState=23000")&&line.toString().contains("vendorCode=1062")&&line.toString().contains("rootType=SQLException")&&!line.toString().contains("private")&&!line.toString().contains("secret-password"))));
  }
 }
 @Test void maliciousLegacyReferencePhaseAndSqlStateAreNeverLoggedOrReturned(){
  try(var log=mockStatic(LogUtils.class)){
   String display=CaseAssetFailureDiagnostics.record("PHASE\nsecret", "https://private.example/key?token=secret\nnext",new SQLException("secret password", "bad\n?", 999));
   assertFalse(display.contains("secret"));assertFalse(display.contains("private.example"));assertTrue(display.contains("UNKNOWN"));
   log.verify(()->LogUtils.error(argThat((Object line)->line.toString().contains("phase=UNKNOWN")&&line.toString().contains("reference=UNKNOWN")&&line.toString().contains("sqlState=UNKNOWN")&&!line.toString().contains("secret")&&!line.toString().contains("private.example")&&!line.toString().contains("\n"))));
  }
 }
}
