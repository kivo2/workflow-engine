package com.example.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.enums.SagaStatus;
import com.example.common.enums.SagaStep;
import com.example.common.messaging.FailureType;
import com.example.common.messaging.WorkerFailedEvent;
import com.example.orchestrator.domain.Saga;
import com.example.orchestrator.domain.SagaRepository;
import com.example.orchestrator.service.CheckoutOrchestrator;

@SpringBootTest
@ActiveProfiles("integrationtest")
@Testcontainers
@TestPropertySource(
    properties = {
      "spring.kafka.listener.auto-startup=false",
      "outbox.relay.enabled=false",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.jpa.hibernate.ddl-auto=create",
      "retry.base-delay-ms=1000",
      "retry.max-delay-ms=30000"
    })
class OrchestratorFailureIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @Autowired private CheckoutOrchestrator orchestrator;
  @Autowired private SagaRepository sagaRepository;

  @Test
  void transientFailure_movesToPendingRetry_withFirstAttemptBackoff() {
    UUID sagaId = seedSaga("transient_cust");
    Instant before = Instant.now();

    orchestrator.handleWorkerFailed(
        failure(sagaId, SagaStep.RESERVE_INVENTORY, FailureType.TRANSIENT));

    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(saga.getStatus()).isEqualTo(SagaStatus.PENDING_RETRY);
    assertThat(saga.getRetryCount()).isZero();
    long deltaMs = Duration.between(before, saga.getNextRetryAt()).toMillis();
    assertThat(deltaMs).isBetween(400L, 1100L);
  }

  @Test
  void permanentFailure_movesToFailed() {
    UUID sagaId = seedSaga("permanent_cust");

    orchestrator.handleWorkerFailed(
        failure(sagaId, SagaStep.RESERVE_INVENTORY, FailureType.PERMANENT));

    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);
    assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.FAILED);
  }

  @Test
  void failureForWrongStep_isIgnored() {
    UUID sagaId = seedSaga("guard_cust");

    orchestrator.handleWorkerFailed(
        failure(sagaId, SagaStep.AUTHORIZE_PAYMENT, FailureType.TRANSIENT));

    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(saga.getStatus()).isEqualTo(SagaStatus.RUNNING);
    assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.RESERVE_INVENTORY);
  }

  private WorkerFailedEvent failure(UUID sagaId, SagaStep step, FailureType type) {
    return new WorkerFailedEvent(sagaId, step, "boom", type, Instant.now());
  }

  private UUID seedSaga(String customerId) {
    StartCheckoutRequest req = new StartCheckoutRequest();
    req.setCustomerId(customerId);
    StartCheckoutRequest.CheckoutItem item = new StartCheckoutRequest.CheckoutItem();
    item.setProductId("prod_1");
    item.setQuantity(1);
    req.setItems(List.of(item));
    StartCheckoutRequest.PaymentMethod pm = new StartCheckoutRequest.PaymentMethod();
    pm.setType("card");
    pm.setToken("tok_test");
    req.setPaymentMethod(pm);
    return orchestrator.startCheckout(req);
  }
}
