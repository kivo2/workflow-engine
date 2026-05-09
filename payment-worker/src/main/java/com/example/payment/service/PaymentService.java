package com.example.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.exceptions.PaymentDeclinedException;
import com.example.common.exceptions.TransientWorkerException;
import com.example.common.interfaces.PaymentWorker;
import com.example.payment.domain.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService implements PaymentWorker {

  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;

  @Override
  @Transactional
  public UUID authorizePayment(UUID sagaId, StartCheckoutRequest.PaymentMethod paymentMethod) {
    log.info("[payment][saga={}] authorizePayment requested", sagaId);

    String token = paymentMethod.getToken();

    if (token.startsWith("tok_declined")) {
      log.warn("[payment][saga={}] Payment declined", sagaId);
      throw new PaymentDeclinedException();
    }

    if (token.startsWith("tok_error")) {
      log.warn("[payment][saga={}] Transient payment error", sagaId);
      throw new TransientWorkerException("Payment provider temporarily unavailable");
    }

    UUID paymentId = UUID.randomUUID();
    int inserted =
        paymentRepository.insertIfAbsent(
            paymentId, sagaId, "AUTHORIZED", BigDecimal.valueOf(99.99), token, Instant.now());
    if (inserted == 0) {
      UUID existingId = paymentRepository.findBySagaId(sagaId).orElseThrow().getPaymentId();
      log.info(
          "[payment][saga={}] Already authorized — duplicate, returning existing paymentId={}",
          sagaId,
          existingId);
      return existingId;
    }

    paymentGateway.charge(sagaId, BigDecimal.valueOf(99.99));

    log.info("[payment][saga={}] Payment AUTHORIZED paymentId={}", sagaId, paymentId);
    return paymentId;
  }
}
