package com.example.orchestrator.dedup;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventDeduplicator {

  private final ProcessedEventRepository processedEventRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean markProcessed(UUID sagaId, String eventType) {
    return processedEventRepository.insertIfAbsent(sagaId, eventType, Instant.now()) == 1;
  }
}
