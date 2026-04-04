package com.example.common.messaging;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAuthorizedEvent implements SagaEvent {
  private UUID sagaId;
  private UUID paymentId;
  private Instant authorizedAt;
}
