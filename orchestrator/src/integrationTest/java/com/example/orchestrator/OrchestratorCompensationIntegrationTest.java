package com.example.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.example.orchestrator.outbox.OutboxRepository;
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
      "retry.max-delay-ms=30000",
      "retry.max-retries=3"
    })
class OrchestratorCompensationIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @Autowired private CheckoutOrchestrator orchestrator;
  @Autowired private SagaRepository sagaRepository;
  @Autowired private OutboxRepository outboxRepository;

  @Test
  void permanentPaymentFailure_afterInventoryReserved_startsCompensation() {
    UUID sagaId = seedSagaAtPayment("comp_pay");

    orchestrator.handleWorkerFailed(
        failure(sagaId, SagaStep.AUTHORIZE_PAYMENT, FailureType.PERMANENT));

    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
    assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.RELEASE_INVENTORY);
    assertThat(releaseCommandCount(sagaId)).isEqualTo(1);
  }

  @Test
  void permanentInventoryFailure_beforeReservation_failsWithoutCompensation() {
    UUID sagaId = seedSaga("comp_inv");

    orchestrator.handleWorkerFailed(
        failure(sagaId, SagaStep.RESERVE_INVENTORY, FailureType.PERMANENT));

    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);
    assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.FAILED);
    assertThat(releaseCommandCount(sagaId)).isZero();
  }

  @Test
  void inventoryReleased_movesToCompensated() {
    UUID sagaId = seedSagaAtPayment("comp_done");
    orchestrator.handleWorkerFailed(
        failure(sagaId, SagaStep.AUTHORIZE_PAYMENT, FailureType.PERMANENT));

    orchestrator.handleInventoryReleased(sagaId);

    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
  }

  @Test
  void duplicatePermanentFailure_doesNotStartCompensationTwice() {
    UUID sagaId = seedSagaAtPayment("comp_dup");
    WorkerFailedEvent event = failure(sagaId, SagaStep.AUTHORIZE_PAYMENT, FailureType.PERMANENT);

    orchestrator.handleWorkerFailed(event);
    orchestrator.handleWorkerFailed(event);

    assertThat(releaseCommandCount(sagaId)).isEqualTo(1);
  }

  @Test
  void staleInventoryReleased_onNonCompensatingSaga_isIgnored() {
    UUID sagaId = seedSaga("comp_stale");

    orchestrator.handleInventoryReleased(sagaId);

    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(saga.getStatus()).isEqualTo(SagaStatus.RUNNING);
  }

  @Test
  void exhaustedRetries_afterInventoryReserved_startsCompensation() {
    UUID sagaId = seedSagaAtPayment("comp_exhaust");
    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    saga.setStatus(SagaStatus.PENDING_RETRY);
    saga.setRetryCount(saga.getMaxRetries());
    saga.setNextRetryAt(Instant.now().minusSeconds(1));
    sagaRepository.saveAndFlush(saga);

    orchestrator.refire(sagaId);

    Saga after = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
    assertThat(after.getCurrentStep()).isEqualTo(SagaStep.RELEASE_INVENTORY);
    assertThat(releaseCommandCount(sagaId)).isEqualTo(1);
  }

  private long releaseCommandCount(UUID sagaId) {
    return outboxRepository.findAll().stream()
        .filter(m -> m.getSagaId().equals(sagaId))
        .filter(m -> "RELEASE_INVENTORY".equals(m.getCommandType()))
        .count();
  }

  private WorkerFailedEvent failure(UUID sagaId, SagaStep step, FailureType type) {
    return new WorkerFailedEvent(sagaId, step, "boom", type, Instant.now());
  }

  private UUID seedSagaAtPayment(String customerId) {
    UUID sagaId = seedSaga(customerId);
    orchestrator.handleInventoryReserved(sagaId, UUID.randomUUID());
    return sagaId;
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
