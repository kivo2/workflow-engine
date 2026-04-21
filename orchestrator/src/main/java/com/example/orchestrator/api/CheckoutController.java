package com.example.orchestrator.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.common.dto.CheckoutResponse;
import com.example.common.dto.StartCheckoutRequest;
import com.example.common.enums.SagaStatus;
import com.example.common.enums.SagaStep;
import com.example.orchestrator.domain.Saga;
import com.example.orchestrator.service.CheckoutOrchestrator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutController {

  private final CheckoutOrchestrator orchestrator;

  @PostMapping
  public ResponseEntity<CheckoutResponse> startCheckout(
      @RequestBody @Valid StartCheckoutRequest request) {
    log.info("POST /checkout customerId={}", request.getCustomerId());
    UUID sagaId = orchestrator.startCheckout(request);
    Saga saga = orchestrator.getStatus(sagaId);
    log.info("POST /checkout response sagaId={} status={}", sagaId, saga.getStatus());
    return ResponseEntity.status(httpStatusFor(saga.getStatus())).body(toResponse(saga));
  }

  @GetMapping("/{sagaId}")
  public ResponseEntity<CheckoutResponse> getCheckoutStatus(@PathVariable UUID sagaId) {
    log.info("GET /checkout/{}", sagaId);
    Saga saga = orchestrator.getStatus(sagaId);
    return ResponseEntity.ok(toResponse(saga));
  }

  private HttpStatus httpStatusFor(SagaStatus status) {
    return switch (status) {
      case COMPLETED -> HttpStatus.CREATED;
      case FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
      case PERMANENTLY_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
      default -> HttpStatus.ACCEPTED;
    };
  }

  private CheckoutResponse toResponse(Saga saga) {
    return CheckoutResponse.builder()
        .sagaId(saga.getSagaId().toString())
        .status(saga.getStatus())
        .currentStep(
            saga.getCurrentStep() != null ? saga.getCurrentStep() : SagaStep.RESERVE_INVENTORY)
        .orderId(saga.getOrderId() != null ? saga.getOrderId().toString() : null)
        .error(saga.getErrorMessage())
        .retryCount(saga.getRetryCount())
        .maxRetries(saga.getMaxRetries())
        .nextRetryAt(saga.getNextRetryAt())
        .build();
  }
}
