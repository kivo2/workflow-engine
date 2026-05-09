package com.example.inventory.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_reservation")
@Getter
@Setter
@NoArgsConstructor
public class InventoryReservation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "saga_id", nullable = false, unique = true)
  private UUID sagaId;

  @Column(name = "reserved_at", nullable = false)
  private Instant reservedAt;

  @Column(name = "released_at")
  private Instant releasedAt;

  public static InventoryReservation of(UUID sagaId) {
    InventoryReservation r = new InventoryReservation();
    r.sagaId = sagaId;
    r.reservedAt = Instant.now();
    return r;
  }
}
