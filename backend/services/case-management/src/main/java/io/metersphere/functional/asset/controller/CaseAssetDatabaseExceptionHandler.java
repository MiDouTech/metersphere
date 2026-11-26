package io.metersphere.functional.asset.controller;
import io.metersphere.functional.asset.service.CaseAssetOperationDiagnostics;
import io.metersphere.system.controller.handler.ResultHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.UUID;
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={CaseAssetController.class,CaseAssetSystemController.class})
public class CaseAssetDatabaseExceptionHandler {
 @ExceptionHandler(DataAccessException.class)
 public ResponseEntity<ResultHolder> handle(DataAccessException error){
  String requestId=UUID.randomUUID().toString();
  String message=CaseAssetOperationDiagnostics.record("ASSET_DATABASE",requestId,error);
  return ResponseEntity.status(500).body(ResultHolder.structuredError(io.metersphere.system.controller.handler.result.MsHttpResultCode.FAILED.getCode(),"api.internalError",message,requestId,true,java.util.Map.of()));
 }
}
