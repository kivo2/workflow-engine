package com.example.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

  @Id
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "saga_id", nullable = false, unique = true)
  private UUID sagaId;

  @Column(name = "customer_id", nullable = false)
  private String customerId;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "total_amount", nullable = false)
  private BigDecimal totalAmount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public static Order create(UUID sagaId, String customerId, BigDecimal totalAmount) {
    Order o = new Order();
    o.orderId = UUID.randomUUID();
    o.sagaId = sagaId;
    o.customerId = customerId;
    o.status = "CREATED";
    o.totalAmount = totalAmount;
    o.createdAt = Instant.now();
    return o;
  }
}
