package io.metersphere.functional.controller;

import io.metersphere.system.controller.handler.annotation.NoResultHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=HandoffController.class)
public class HandoffExceptionHandler {
    @NoResultHolder
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String,Object>> invalid(Exception ignored){return ResponseEntity.badRequest().body(Map.of("code","INVALID_HANDOFF_INPUT","message","交接配置或请求参数无效，请核对输入","retryable",false));}
    @NoResultHolder
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String,Object>> conflict(IllegalStateException ignored){return ResponseEntity.status(409).body(Map.of("code","HANDOFF_STATE_CONFLICT","message","当前状态或预算不允许该操作，请刷新并重新预览","retryable",false));}
}
