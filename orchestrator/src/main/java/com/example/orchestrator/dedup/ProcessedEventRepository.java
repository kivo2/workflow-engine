package com.example.orchestrator.dedup;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

  @Modifying
  @Query(
      value =
          "INSERT INTO processed_event (saga_id, event_type, processed_at)"
              + " VALUES (:sagaId, :eventType, :processedAt) ON CONFLICT (saga_id, event_type) DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("sagaId") UUID sagaId,
      @Param("eventType") String eventType,
      @Param("processedAt") Instant processedAt);
}
