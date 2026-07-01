package com.example.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.exceptions.PaymentDeclinedException;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.service.PaymentService;

@SpringBootTest
@ActiveProfiles("integrationtest")
@Testcontainers
@Transactional
class PaymentIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @Autowired private PaymentService service;
  @Autowired private PaymentRepository paymentRepository;

  @Test
  void happyPath_createsPaymentRecord() {
    UUID sagaId = UUID.randomUUID();
    UUID paymentId = service.authorizePayment(sagaId, pm("tok_abc"));

    assertThat(paymentId).isNotNull();
    var payment = paymentRepository.findBySagaId(sagaId);
    assertThat(payment).isPresent();
    assertThat(payment.get().getStatus()).isEqualTo("AUTHORIZED");
  }

  @Test
  void declined_throwsAndCreatesNoRecord() {
    UUID sagaId = UUID.randomUUID();
    assertThatThrownBy(() -> service.authorizePayment(sagaId, pm("tok_declined")))
        .isInstanceOf(PaymentDeclinedException.class);
    assertThat(paymentRepository.findBySagaId(sagaId)).isEmpty();
  }

  @Test
  void idempotency_secondCallReturnsSamePaymentId() {
    UUID sagaId = UUID.randomUUID();
    UUID first = service.authorizePayment(sagaId, pm("tok_abc"));
    UUID second = service.authorizePayment(sagaId, pm("tok_abc"));
    assertThat(first).isEqualTo(second);
    assertThat(paymentRepository.findAll()).hasSize(1);
  }

  private StartCheckoutRequest.PaymentMethod pm(String token) {
    StartCheckoutRequest.PaymentMethod p = new StartCheckoutRequest.PaymentMethod();
    p.setType("card");
    p.setToken(token);
    return p;
  }
}
