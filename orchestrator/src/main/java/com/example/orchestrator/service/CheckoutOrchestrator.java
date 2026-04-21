package com.example.orchestrator.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.enums.SagaStatus;
import com.example.common.enums.SagaStep;
import com.example.common.messaging.CommandType;
import com.example.common.messaging.FailureType;
import com.example.common.messaging.WorkerFailedEvent;
import com.example.orchestrator.dedup.EventDeduplicator;
import com.example.orchestrator.domain.Saga;
import com.example.orchestrator.domain.SagaNotFoundException;
import com.example.orchestrator.domain.SagaRepository;
import com.example.orchestrator.outbox.CommandOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutOrchestrator {

  private final SagaRepository sagaRepository;
  private final SagaStateService sagaStateService;
  private final CommandOutbox commandOutbox;
  private final EventDeduplicator eventDeduplicator;
  private final BackoffCalculator backoffCalculator;
  private final ObjectMapper objectMapper;

  private static final String INVENTORY_RESERVED = "INVENTORY_RESERVED";
  private static final String PAYMENT_AUTHORIZED = "PAYMENT_AUTHORIZED";
  private static final String ORDER_CREATED = "ORDER_CREATED";

  @Value("${retry.max-retries:3}")
  private int maxRetries;

  @Transactional
  public UUID startCheckout(StartCheckoutRequest request) {
    Saga saga = sagaStateService.createSaga(request.getCustomerId(), maxRetries);
    log.info(
        "[saga={}] Checkout started for customer={}", saga.getSagaId(), request.getCustomerId());

    try {
      String requestJson = objectMapper.writeValueAsString(request);
      saga.setRequestPayload(requestJson);
      sagaRepository.save(saga);
      log.debug(
          "[saga={}] Stored request payload ({} bytes)", saga.getSagaId(), requestJson.length());
    } catch (Exception e) {
      log.error("[saga={}] Failed to serialize request payload", saga.getSagaId(), e);
      throw new RuntimeException(
          "Failed to serialize request payload for saga=" + saga.getSagaId(), e);
    }

    commandOutbox.enqueue(saga.getSagaId(), CommandType.RESERVE_INVENTORY, request);

    return saga.getSagaId();
  }

  @Transactional(readOnly = true)
  public Saga getStatus(UUID sagaId) {
    return sagaRepository.findById(sagaId).orElseThrow(() -> new SagaNotFoundException(sagaId));
  }

  @Transactional
  public void handleInventoryReserved(UUID sagaId, UUID reservationId) {
    log.info(
        "[saga={}] Inventory reserved reservationId={}, advancing to AUTHORIZE_PAYMENT",
        sagaId,
        reservationId);

    if (!eventDeduplicator.markProcessed(sagaId, INVENTORY_RESERVED)) {
      log.info("[saga={}] Duplicate INVENTORY_RESERVED event, skipping", sagaId);
      return;
    }

    Saga saga =
        sagaRepository.findById(sagaId).orElseThrow(() -> new SagaNotFoundException(sagaId));

    if (saga.getCurrentStep() != SagaStep.RESERVE_INVENTORY) {
      log.error(
          "[saga={}] Illegal transition: INVENTORY_RESERVED event but saga is at {} (expected {}); not advancing",
          sagaId,
          saga.getCurrentStep(),
          SagaStep.RESERVE_INVENTORY);
      return;
    }

    sagaStateService.advanceStep(sagaId, SagaStep.AUTHORIZE_PAYMENT);

    StartCheckoutRequest request = buildRequestFromSaga(saga);
    commandOutbox.enqueue(sagaId, CommandType.AUTHORIZE_PAYMENT, request);
  }

  @Transactional
  public void handlePaymentAuthorized(UUID sagaId, UUID paymentId) {
    log.info(
        "[saga={}] Payment authorized paymentId={}, advancing to CREATE_ORDER", sagaId, paymentId);

    if (!eventDeduplicator.markProcessed(sagaId, PAYMENT_AUTHORIZED)) {
      log.info("[saga={}] Duplicate PAYMENT_AUTHORIZED event, skipping", sagaId);
      return;
    }

    Saga saga =
        sagaRepository.findById(sagaId).orElseThrow(() -> new SagaNotFoundException(sagaId));

    if (saga.getCurrentStep() != SagaStep.AUTHORIZE_PAYMENT) {
      log.error(
          "[saga={}] Illegal transition: PAYMENT_AUTHORIZED event but saga is at {} (expected {}); not advancing",
          sagaId,
          saga.getCurrentStep(),
          SagaStep.AUTHORIZE_PAYMENT);
      return;
    }

    sagaStateService.recordPayment(sagaId, paymentId);
    sagaStateService.advanceStep(sagaId, SagaStep.CREATE_ORDER);

    StartCheckoutRequest request = buildRequestFromSaga(saga);
    commandOutbox.enqueue(sagaId, CommandType.CREATE_ORDER, request);
  }

  @Transactional
  public void handleOrderCreated(UUID sagaId, UUID orderId) {
    log.info("[saga={}] Order created orderId={}, marking COMPLETED", sagaId, orderId);

    if (!eventDeduplicator.markProcessed(sagaId, ORDER_CREATED)) {
      log.info("[saga={}] Duplicate ORDER_CREATED event, skipping", sagaId);
      return;
    }

    Saga saga =
        sagaRepository.findById(sagaId).orElseThrow(() -> new SagaNotFoundException(sagaId));

    if (saga.getCurrentStep() != SagaStep.CREATE_ORDER) {
      log.error(
          "[saga={}] Illegal transition: ORDER_CREATED event but saga is at {} (expected {}); not completing",
          sagaId,
          saga.getCurrentStep(),
          SagaStep.CREATE_ORDER);
      return;
    }

    sagaStateService.completeSaga(sagaId, orderId);
  }

  @Transactional
  public void handleWorkerFailed(WorkerFailedEvent event) {
    UUID sagaId = event.getSagaId();
    log.info(
        "[saga={}] Worker reported {} failure at {}: {}",
        sagaId,
        event.getFailureType(),
        event.getFailedStep(),
        event.getReason());

    Saga saga =
        sagaRepository.findById(sagaId).orElseThrow(() -> new SagaNotFoundException(sagaId));

    if (saga.getStatus() != SagaStatus.RUNNING || saga.getCurrentStep() != event.getFailedStep()) {
      log.info(
          "[saga={}] Ignoring {} failure at {} — saga is {}/{}",
          sagaId,
          event.getFailureType(),
          event.getFailedStep(),
          saga.getStatus(),
          saga.getCurrentStep());
      return;
    }

    saga.setLastFailureReason(event.getReason());

    if (event.getFailureType() == FailureType.TRANSIENT) {
      saga.setStatus(SagaStatus.PENDING_RETRY);
      saga.setNextRetryAt(backoffCalculator.nextRetryAt(saga.getRetryCount()));
      sagaRepository.save(saga);
      log.info(
          "[saga={}] Transient failure at {} — PENDING_RETRY (attempt {}), next retry at {}",
          sagaId,
          event.getFailedStep(),
          saga.getRetryCount(),
          saga.getNextRetryAt());
      return;
    }

    if (hasReservedInventory(saga)) {
      beginInventoryCompensation(saga, event.getReason());
    } else {
      saga.setStatus(SagaStatus.FAILED);
      saga.setCurrentStep(SagaStep.FAILED);
      saga.setErrorMessage(event.getReason());
      sagaRepository.save(saga);
      log.warn(
          "[saga={}] Permanent failure at {} — FAILED (nothing to compensate)",
          sagaId,
          event.getFailedStep());
    }
  }

  @Transactional
  public void handleInventoryReleased(UUID sagaId) {
    Saga saga =
        sagaRepository.findById(sagaId).orElseThrow(() -> new SagaNotFoundException(sagaId));

    if (saga.getStatus() != SagaStatus.COMPENSATING
        || saga.getCurrentStep() != SagaStep.RELEASE_INVENTORY) {
      log.info(
          "[saga={}] Ignoring InventoryReleased — saga is {}/{}",
          sagaId,
          saga.getStatus(),
          saga.getCurrentStep());
      return;
    }

    saga.setStatus(SagaStatus.COMPENSATED);
    sagaRepository.save(saga);
    log.info("[saga={}] Inventory released — COMPENSATED", sagaId);
  }

  private boolean hasReservedInventory(Saga saga) {
    SagaStep step = saga.getCurrentStep();
    return step == SagaStep.AUTHORIZE_PAYMENT || step == SagaStep.CREATE_ORDER;
  }

  private void beginInventoryCompensation(Saga saga, String reason) {
    saga.setStatus(SagaStatus.COMPENSATING);
    saga.setCurrentStep(SagaStep.RELEASE_INVENTORY);
    saga.setErrorMessage(reason);
    saga.setNextRetryAt(null);
    sagaRepository.saveAndFlush(saga);
    commandOutbox.enqueue(
        saga.getSagaId(), CommandType.RELEASE_INVENTORY, buildRequestFromSaga(saga));
    log.warn("[saga={}] COMPENSATING — releasing inventory (was: {})", saga.getSagaId(), reason);
  }

  @Transactional
  public void refire(UUID sagaId) {
    Saga saga =
        sagaRepository.findById(sagaId).orElseThrow(() -> new SagaNotFoundException(sagaId));

    if (saga.getStatus() != SagaStatus.PENDING_RETRY
        || saga.getNextRetryAt() == null
        || saga.getNextRetryAt().isAfter(Instant.now())) {
      return;
    }

    int attemptsDone = saga.getRetryCount();
    if (attemptsDone >= saga.getMaxRetries()) {
      if (hasReservedInventory(saga)) {
        beginInventoryCompensation(
            saga, "Exhausted " + attemptsDone + " retries: " + saga.getLastFailureReason());
        log.warn(
            "[saga={}] Max retries ({}) exhausted — COMPENSATING", sagaId, saga.getMaxRetries());
        return;
      }
      saga.setStatus(SagaStatus.PERMANENTLY_FAILED);
      saga.setNextRetryAt(null);
      saga.setErrorMessage(
          "Permanently failed after "
              + attemptsDone
              + " retries. Last error: "
              + saga.getLastFailureReason());
      sagaRepository.save(saga);
      commandOutbox.enqueueToDeadLetter(
          sagaId, commandForStep(saga.getCurrentStep()), buildRequestFromSaga(saga));
      log.warn(
          "[saga={}] Max retries ({}) exhausted — PERMANENTLY_FAILED, dead-lettered",
          sagaId,
          saga.getMaxRetries());
      return;
    }

    int attempt = attemptsDone + 1;
    saga.setRetryCount(attempt);
    saga.setStatus(SagaStatus.RUNNING);
    saga.setNextRetryAt(null);
    sagaRepository.saveAndFlush(saga);

    CommandType command = commandForStep(saga.getCurrentStep());
    commandOutbox.enqueue(sagaId, command, buildRequestFromSaga(saga));
    log.info(
        "[saga={}] Re-firing {} (retry {}/{})", sagaId, command, attempt, saga.getMaxRetries());
  }

  private CommandType commandForStep(SagaStep step) {
    return switch (step) {
      case RESERVE_INVENTORY -> CommandType.RESERVE_INVENTORY;
      case AUTHORIZE_PAYMENT -> CommandType.AUTHORIZE_PAYMENT;
      case CREATE_ORDER -> CommandType.CREATE_ORDER;
      default -> throw new IllegalStateException("Cannot retry saga at step " + step);
    };
  }

  private StartCheckoutRequest buildRequestFromSaga(Saga saga) {
    String payload = saga.getRequestPayload();
    if (payload == null) {
      throw new IllegalStateException(
          "No request payload stored for saga=" + saga.getSagaId() + "; cannot rebuild request");
    }
    try {
      return objectMapper.readValue(payload, StartCheckoutRequest.class);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to deserialize request payload for saga=" + saga.getSagaId(), e);
    }
  }
}
