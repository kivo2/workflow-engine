package com.example.orchestrator.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.orchestrator.domain.SagaNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(SagaNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleSagaNotFound(SagaNotFoundException ex) {
    log.warn("Saga not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(errorBody(HttpStatus.NOT_FOUND, ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
    log.warn("Validation failed: {}", errors);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", 400);
    body.put("error", "Bad Request");
    body.put("messages", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
    log.error("Unexpected error: {}", ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"));
  }

  private Map<String, Object> errorBody(HttpStatus status, String message) {
    return Map.of(
        "timestamp", Instant.now().toString(),
        "status", status.value(),
        "error", status.getReasonPhrase(),
        "message", message);
  }
}
