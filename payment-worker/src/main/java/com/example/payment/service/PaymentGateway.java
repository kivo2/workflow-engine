package com.example.payment.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PaymentGateway {

  public void charge(UUID idempotencyKey, BigDecimal amount) {
    log.info(
        "[gateway] charge amount={} idempotencyKey={} (stub — no external call)",
        amount,
        idempotencyKey);
  }
}
