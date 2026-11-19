package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentApiErrorDTO;
import io.metersphere.agent.service.BusinessGovernanceException;
import io.metersphere.sdk.exception.MSException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes = {
      BusinessAssetController.class,
      SharedConfigurationController.class,
      ExternalExecutorController.class
    })
public class BusinessGovernanceExceptionHandler {
  private static final Logger LOG =
      LoggerFactory.getLogger(BusinessGovernanceExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  public ResponseEntity<AgentApiErrorDTO> handle(Exception error, HttpServletRequest request) {
    String trace = UUID.randomUUID().toString(),
        code = "BUSINESS_ASSET_INTERNAL_ERROR",
        message = "服务暂时不可用，请稍后重试并提供追踪编号";
    int status = 500;
    if (error instanceof BusinessGovernanceException safe) {
      code = safe.code();
      message = safe.getMessage();
      status = 400;
      if (code.endsWith("CONFLICT")) status = 409;
      else if (code.endsWith("NOT_ACCESSIBLE")) status = 403;
      else if (code.equals("CONFIGURATION_KEY_UNAVAILABLE")) status = 503;
    } else if (error instanceof MSException) {
      code = "RESOURCE_NOT_ACCESSIBLE";
      message = "资源不可访问，请检查项目范围与权限";
      status = 403;
    } else if (error instanceof org.springframework.web.bind.MethodArgumentNotValidException
        || error instanceof org.springframework.http.converter.HttpMessageNotReadableException
        || error instanceof org.springframework.web.bind.MissingServletRequestParameterException) {
      code = "VALIDATION_ERROR";
      message = "提交字段不符合要求，请检查必填项、长度和格式";
      status = 400;
    }
    io.metersphere.agent.security.AgentSafeDiagnostics.failure(
        LOG, "BUSINESS_GOVERNANCE_FAILURE", "REQUEST", trace, code, error);
    return ResponseEntity.status(status).body(new AgentApiErrorDTO(code, message, Map.of(), trace));
  }
}
