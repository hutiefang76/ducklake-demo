package com.lanxinai.data.paltform.ducklake.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception) {
        log.warn("API 参数校验失败：{}", exception.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", exception.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> serverError(Exception exception) {
        String errorId = UUID.randomUUID().toString();
        log.error("DuckLake API 执行失败，errorId={}", errorId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", 500,
                "error", "DuckLake operation failed",
                "errorId", errorId,
                "exceptionType", exception.getClass().getSimpleName(),
                "timestamp", Instant.now().toString()
        ));
    }
}
