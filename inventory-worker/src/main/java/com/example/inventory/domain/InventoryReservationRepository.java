package com.example.inventory.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

  Optional<InventoryReservation> findBySagaId(UUID sagaId);

  @Modifying
  @Query(
      value =
          "INSERT INTO inventory_reservation (saga_id, reserved_at) VALUES (:sagaId, :reservedAt)"
              + " ON CONFLICT (saga_id) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(@Param("sagaId") UUID sagaId, @Param("reservedAt") Instant reservedAt);

  @Modifying
  @Query(
      value =
          "UPDATE inventory_reservation SET released_at = :releasedAt"
              + " WHERE saga_id = :sagaId AND released_at IS NULL",
      nativeQuery = true)
  int markReleased(@Param("sagaId") UUID sagaId, @Param("releasedAt") Instant releasedAt);
}
