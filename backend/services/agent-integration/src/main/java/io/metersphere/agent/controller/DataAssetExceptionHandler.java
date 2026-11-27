package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentApiErrorDTO;
import io.metersphere.agent.service.DataAssetErrors;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Exact data/global asset transport boundary, before generic execution error handling. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes = {
      DataAssetController.class,
      DataAssetRecordController.class,
      DataAssetLegacyAutoController.class,
      DataAssetPublicationController.class,
      GlobalAssetCatalogController.class,
      TestAssetController.class
    })
public class DataAssetExceptionHandler {
  @io.metersphere.system.controller.handler.annotation.NoResultHolder
  @ExceptionHandler(Exception.class)
  public ResponseEntity<AgentApiErrorDTO> handle(Exception error, HttpServletRequest request) {
    Object previous = request.getAttribute("agentTraceId");
    String trace =
        previous instanceof String value
                && value.matches(
                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
            ? value
            : UUID.randomUUID().toString();
    request.setAttribute("agentTraceId", trace);
    var safe = DataAssetErrors.map(error);
    io.metersphere.agent.security.AgentSafeDiagnostics.failure(
        org.slf4j.LoggerFactory.getLogger(DataAssetExceptionHandler.class),
        "DATA_ASSET_REQUEST_FAILURE",
        "request",
        trace,
        safe.code(),
        error);
    return ResponseEntity.status(safe.status())
        .body(new AgentApiErrorDTO(safe.code(), safe.message(), Map.of(), trace));
  }
}
