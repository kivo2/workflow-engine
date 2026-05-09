package com.example.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
  Optional<Order> findBySagaId(UUID sagaId);

  @Modifying
  @Query(
      value =
          "INSERT INTO orders (order_id, saga_id, customer_id, status, total_amount, created_at)"
              + " VALUES (:orderId, :sagaId, :customerId, :status, :totalAmount, :createdAt)"
              + " ON CONFLICT (saga_id) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("orderId") UUID orderId,
      @Param("sagaId") UUID sagaId,
      @Param("customerId") String customerId,
      @Param("status") String status,
      @Param("totalAmount") BigDecimal totalAmount,
      @Param("createdAt") Instant createdAt);
}
