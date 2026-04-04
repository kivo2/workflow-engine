package com.example.common.messaging;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservedEvent implements SagaEvent {
  private UUID sagaId;
  private UUID reservationId;
  private Instant reservedAt;
}
