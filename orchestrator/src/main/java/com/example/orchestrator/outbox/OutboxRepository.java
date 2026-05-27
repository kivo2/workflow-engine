package com.example.orchestrator.outbox;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

  List<OutboxMessage> findByStatusOrderByIdAsc(OutboxStatus status, Pageable pageable);
}
