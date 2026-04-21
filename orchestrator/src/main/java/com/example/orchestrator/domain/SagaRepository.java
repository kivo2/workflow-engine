package com.example.orchestrator.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.enums.SagaStatus;

@Repository
public interface SagaRepository extends JpaRepository<Saga, UUID> {

  List<Saga> findByStatusAndNextRetryAtBefore(SagaStatus status, Instant now, Pageable pageable);

  List<Saga> findByStatusInAndUpdatedAtBeforeAndFlaggedStuckAtIsNull(
      Collection<SagaStatus> statuses, Instant threshold, Pageable pageable);

  @Transactional
  @Modifying
  @Query(
      "UPDATE Saga s SET s.flaggedStuckAt = :now"
          + " WHERE s.sagaId IN :ids AND s.flaggedStuckAt IS NULL"
          + " AND s.status IN :statuses AND s.updatedAt < :threshold")
  int markFlaggedStuck(
      @Param("ids") Collection<UUID> ids,
      @Param("statuses") Collection<SagaStatus> statuses,
      @Param("threshold") Instant threshold,
      @Param("now") Instant now);
}
