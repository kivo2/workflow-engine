package com.example.payment.domain;

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
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  Optional<Payment> findBySagaId(UUID sagaId);

  @Modifying
  @Query(
      value =
          "INSERT INTO payment (payment_id, saga_id, status, amount, payment_token, authorized_at)"
              + " VALUES (:paymentId, :sagaId, :status, :amount, :token, :authorizedAt)"
              + " ON CONFLICT (saga_id) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("paymentId") UUID paymentId,
      @Param("sagaId") UUID sagaId,
      @Param("status") String status,
      @Param("amount") BigDecimal amount,
      @Param("token") String token,
      @Param("authorizedAt") Instant authorizedAt);
}
