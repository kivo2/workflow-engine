package com.example.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.exceptions.PaymentDeclinedException;
import com.example.common.exceptions.TransientWorkerException;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private PaymentGateway paymentGateway;
  @InjectMocks private PaymentService service;

  private UUID sagaId;

  @BeforeEach
  void setUp() {
    sagaId = UUID.randomUUID();
  }

  @Test
  void authorizePayment_happyPath_returnsPaymentId() {
    when(paymentRepository.insertIfAbsent(any(), eq(sagaId), any(), any(), any(), any()))
        .thenReturn(1);
    UUID paymentId = service.authorizePayment(sagaId, paymentMethod("tok_abc"));
    assertThat(paymentId).isNotNull();
    verify(paymentGateway).charge(eq(sagaId), any());
  }

  @Test
  void authorizePayment_declined_throwsPermanentException() {
    assertThatThrownBy(() -> service.authorizePayment(sagaId, paymentMethod("tok_declined")))
        .isInstanceOf(PaymentDeclinedException.class);
    verify(paymentRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
  }

  @Test
  void authorizePayment_transientError_throwsRetryableException() {
    assertThatThrownBy(() -> service.authorizePayment(sagaId, paymentMethod("tok_error")))
        .isInstanceOf(TransientWorkerException.class);
    verify(paymentRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
  }

  @Test
  void authorizePayment_idempotency_returnsExistingPaymentId() {
    Payment existing = Payment.authorized(sagaId, "tok_abc", java.math.BigDecimal.TEN);
    when(paymentRepository.insertIfAbsent(any(), eq(sagaId), any(), any(), any(), any()))
        .thenReturn(0);
    when(paymentRepository.findBySagaId(sagaId)).thenReturn(Optional.of(existing));

    UUID result = service.authorizePayment(sagaId, paymentMethod("tok_abc"));

    assertThat(result).isEqualTo(existing.getPaymentId());
    verify(paymentGateway, never()).charge(any(), any());
  }

  private StartCheckoutRequest.PaymentMethod paymentMethod(String token) {
    StartCheckoutRequest.PaymentMethod pm = new StartCheckoutRequest.PaymentMethod();
    pm.setType("card");
    pm.setToken(token);
    return pm;
  }
}
