package io.metersphere.functional.controller;

import io.metersphere.functional.planreview.PlanReviewFailure;
import io.metersphere.system.controller.handler.annotation.NoResultHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Preserve the safe business failure on existing plan/member controllers, without logging payloads. */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice(basePackages = {"io.metersphere.functional.controller", "io.metersphere.plan.controller"})
public class PlanReviewExceptionHandler {
    @NoResultHolder
    @ExceptionHandler(PlanReviewFailure.class)
    public ResponseEntity<Map<String, Object>> failure(PlanReviewFailure error) {
        return ResponseEntity.status(error.httpStatus()).body(Map.of("code", error.code(), "message", error.getMessage(), "retryable", false));
    }
}
