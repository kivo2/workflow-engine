package com.example.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

  @Id
  @Column(name = "payment_id", nullable = false)
  private UUID paymentId;

  @Column(name = "saga_id", nullable = false, unique = true)
  private UUID sagaId;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  @Column(name = "payment_token", nullable = false)
  private String paymentToken;

  @Column(name = "authorized_at", nullable = false)
  private Instant authorizedAt;

  public static Payment authorized(UUID sagaId, String token, BigDecimal amount) {
    Payment p = new Payment();
    p.paymentId = UUID.randomUUID();
    p.sagaId = sagaId;
    p.status = "AUTHORIZED";
    p.amount = amount;
    p.paymentToken = token;
    p.authorizedAt = Instant.now();
    return p;
  }
}
