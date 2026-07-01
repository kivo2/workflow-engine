package com.example.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.example.common.enums.SagaStep;
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
      "spring.jpa.hibernate.ddl-auto=create"
    })
class OrchestratorDedupIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @Autowired private CheckoutOrchestrator orchestrator;
  @Autowired private SagaRepository sagaRepository;
  @Autowired private OutboxRepository outboxRepository;

  @Test
  void duplicateInventoryReservedEvent_advancesSagaExactlyOnce() {
    UUID sagaId = seedSaga("dedup_cust");

    orchestrator.handleInventoryReserved(sagaId, UUID.randomUUID());
    orchestrator.handleInventoryReserved(sagaId, UUID.randomUUID());

    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.AUTHORIZE_PAYMENT);
    assertThat(outboxCount(sagaId, "AUTHORIZE_PAYMENT")).isEqualTo(1);
  }

  @Test
  void illegalTransition_failsLoudAndDoesNotAdvance() {
    UUID sagaId = seedSaga("illegal_cust");

    orchestrator.handlePaymentAuthorized(sagaId, UUID.randomUUID());

    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.RESERVE_INVENTORY);
    assertThat(outboxCount(sagaId, "CREATE_ORDER")).isZero();
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

  private long outboxCount(UUID sagaId, String commandType) {
    return outboxRepository.findAll().stream()
        .filter(m -> m.getSagaId().equals(sagaId) && m.getCommandType().equals(commandType))
        .count();
  }
}
