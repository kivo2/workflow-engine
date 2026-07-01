package com.example.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.enums.SagaStatus;
import com.example.orchestrator.domain.Saga;
import com.example.orchestrator.domain.SagaRepository;
import com.example.orchestrator.scheduler.StuckSagaDetector;
import com.example.orchestrator.service.CheckoutOrchestrator;

@SpringBootTest
@ActiveProfiles("integrationtest")
@Testcontainers
@TestPropertySource(
    properties = {
      "spring.kafka.listener.auto-startup=false",
      "spring.kafka.admin.fail-fast=false",
      "outbox.relay.enabled=false",
      "stuck.detector.auto-enabled=false",
      "stuck.detector.timeout-ms=60000",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.jpa.hibernate.ddl-auto=create"
    })
class StuckSagaDetectorIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @Autowired private StuckSagaDetector detector;
  @Autowired private CheckoutOrchestrator orchestrator;
  @Autowired private SagaRepository sagaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void stuckRunningSaga_isFlagged() {
    UUID sagaId = seedSaga("stuck_run");
    backdate(sagaId, Duration.ofMinutes(10));

    detector.flagStuckSagas();

    assertThat(flaggedStuckAt(sagaId)).isNotNull();
  }

  @Test
  void stuckCompensatingSaga_isFlagged() {
    UUID sagaId = seedSaga("stuck_comp");
    setStatus(sagaId, SagaStatus.COMPENSATING);
    backdate(sagaId, Duration.ofMinutes(10));

    detector.flagStuckSagas();

    assertThat(flaggedStuckAt(sagaId)).isNotNull();
  }

  @Test
  void recentlyUpdatedSaga_isNotFlagged() {
    UUID sagaId = seedSaga("fresh");

    detector.flagStuckSagas();

    assertThat(flaggedStuckAt(sagaId)).isNull();
  }

  @Test
  void terminalSaga_isNotFlagged() {
    UUID sagaId = seedSaga("done");
    setStatus(sagaId, SagaStatus.COMPLETED);
    backdate(sagaId, Duration.ofMinutes(10));

    detector.flagStuckSagas();

    assertThat(flaggedStuckAt(sagaId)).isNull();
  }

  @Test
  void alreadyFlaggedSaga_isNotReFlagged() {
    UUID sagaId = seedSaga("once");
    backdate(sagaId, Duration.ofMinutes(10));
    detector.flagStuckSagas();
    Instant firstFlag = flaggedStuckAt(sagaId);
    assertThat(firstFlag).isNotNull();

    detector.flagStuckSagas();

    assertThat(flaggedStuckAt(sagaId)).isEqualTo(firstFlag);
  }

  private Instant flaggedStuckAt(UUID sagaId) {
    return sagaRepository.findById(sagaId).orElseThrow().getFlaggedStuckAt();
  }

  private void setStatus(UUID sagaId, SagaStatus status) {
    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    saga.setStatus(status);
    sagaRepository.saveAndFlush(saga);
  }

  private void backdate(UUID sagaId, Duration ago) {
    jdbcTemplate.update(
        "UPDATE saga SET updated_at = ? WHERE saga_id = ?",
        Timestamp.from(Instant.now().minus(ago)),
        sagaId);
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
