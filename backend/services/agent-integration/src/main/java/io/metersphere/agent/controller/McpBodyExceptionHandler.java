package io.metersphere.agent.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=AgentMcpStreamableController.class)
public class McpBodyExceptionHandler {
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @io.metersphere.system.controller.handler.annotation.NoResultHolder
  public ResponseEntity<Map<String,Object>> invalidBody(HttpMessageNotReadableException error) {
    String trace=java.util.UUID.randomUUID().toString();
    io.metersphere.agent.security.AgentSafeDiagnostics.failure(
      org.slf4j.LoggerFactory.getLogger(McpBodyExceptionHandler.class),
      "MCP_REQUEST_BODY_FAILURE", "decode", trace, "MCP_PARSE_ERROR", error);
    Map<String,Object> body=new LinkedHashMap<>();
    body.put("jsonrpc","2.0"); body.put("id",null);
    body.put("error",Map.of("code",-32700,"message","请求内容无效或超过容量限制，请检查JSON格式并减少数据量","data",Map.of("traceId",trace)));
    return ResponseEntity.badRequest().body(body);
  }
}
