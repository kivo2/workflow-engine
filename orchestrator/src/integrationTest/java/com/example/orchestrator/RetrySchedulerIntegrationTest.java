package com.example.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.enums.SagaStatus;
import com.example.orchestrator.domain.Saga;
import com.example.orchestrator.domain.SagaRepository;
import com.example.orchestrator.outbox.OutboxRepository;
import com.example.orchestrator.scheduler.RetryScheduler;
import com.example.orchestrator.service.CheckoutOrchestrator;

@SpringBootTest
@ActiveProfiles("integrationtest")
@Testcontainers
@TestPropertySource(
    properties = {
      "spring.kafka.listener.auto-startup=false",
      "outbox.relay.enabled=false",
      "retry.scheduler.auto-enabled=false",
      "retry.max-retries=3",
      "retry.base-delay-ms=1000",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.jpa.hibernate.ddl-auto=create"
    })
class RetrySchedulerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @Autowired private RetryScheduler scheduler;
  @Autowired private CheckoutOrchestrator orchestrator;
  @Autowired private SagaRepository sagaRepository;
  @Autowired private OutboxRepository outboxRepository;

  @Test
  void readyPendingRetry_isRefired_incrementsAttemptAndReEnqueues() {
    UUID sagaId = seedPendingRetry(0);
    long before = commandCount(sagaId, "RESERVE_INVENTORY");

    scheduler.pollAndRefire();

    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(saga.getStatus()).isEqualTo(SagaStatus.RUNNING);
    assertThat(saga.getRetryCount()).isEqualTo(1);
    assertThat(saga.getNextRetryAt()).isNull();
    assertThat(commandCount(sagaId, "RESERVE_INVENTORY")).isEqualTo(before + 1);
    assertThat(dlqCount(sagaId)).isZero();
  }

  @Test
  void exhaustedRetries_movesToPermanentlyFailed_andDeadLetters() {
    UUID sagaId = seedPendingRetry(3);
    long beforeCommands = commandCount(sagaId, "RESERVE_INVENTORY");

    scheduler.pollAndRefire();

    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(saga.getStatus()).isEqualTo(SagaStatus.PERMANENTLY_FAILED);
    assertThat(saga.getNextRetryAt()).isNull();
    assertThat(commandCount(sagaId, "RESERVE_INVENTORY")).isEqualTo(beforeCommands);
    assertThat(dlqCount(sagaId)).isEqualTo(1);
  }

  @Test
  void concurrentWrite_triggersOptimisticLockOnStaleView() {
    UUID sagaId = seedPendingRetry(0);
    Saga staleView = sagaRepository.findById(sagaId).orElseThrow();

    orchestrator.refire(sagaId);

    staleView.setRetryCount(99);
    assertThatThrownBy(() -> sagaRepository.saveAndFlush(staleView))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  private UUID seedPendingRetry(int retryCount) {
    UUID sagaId = seedSaga("retry_cust");
    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    saga.setStatus(SagaStatus.PENDING_RETRY);
    saga.setRetryCount(retryCount);
    saga.setNextRetryAt(Instant.now().minusSeconds(1));
    saga.setLastFailureReason("boom");
    sagaRepository.saveAndFlush(saga);
    return sagaId;
  }

  private long commandCount(UUID sagaId, String commandType) {
    return outboxRepository.findAll().stream()
        .filter(m -> m.getSagaId().equals(sagaId))
        .filter(m -> m.getTopic().equals("checkout.commands"))
        .filter(m -> m.getCommandType().equals(commandType))
        .count();
  }

  private long dlqCount(UUID sagaId) {
    return outboxRepository.findAll().stream()
        .filter(m -> m.getSagaId().equals(sagaId))
        .filter(m -> m.getTopic().equals("checkout.commands.DLT"))
        .count();
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
