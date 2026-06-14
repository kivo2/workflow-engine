package com.example.orchestrator.scheduler;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.common.enums.SagaStatus;
import com.example.orchestrator.domain.Saga;
import com.example.orchestrator.domain.SagaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StuckSagaDetector {

  private static final List<SagaStatus> IN_FLIGHT =
      List.of(SagaStatus.RUNNING, SagaStatus.COMPENSATING);

  private final SagaRepository sagaRepository;

  @Value("${stuck.detector.auto-enabled:true}")
  private boolean autoEnabled;

  @Value("${stuck.detector.timeout-ms:60000}")
  private long timeoutMs;

  @Value("${stuck.detector.batch-size:100}")
  private int batchSize;

  @Scheduled(fixedDelayString = "${stuck.detector.interval-ms:10000}")
  public void scheduledScan() {
    if (autoEnabled) {
      flagStuckSagas();
    }
  }

  public void flagStuckSagas() {
    Instant threshold = Instant.now().minusMillis(timeoutMs);
    List<Saga> stuck =
        sagaRepository.findByStatusInAndUpdatedAtBeforeAndFlaggedStuckAtIsNull(
            IN_FLIGHT, threshold, PageRequest.of(0, batchSize, Sort.by("updatedAt")));
    if (stuck.isEmpty()) {
      return;
    }

    for (Saga saga : stuck) {
      log.error(
          "[saga={}] STUCK in {}/{} — no progress since {} (> {} ms) — flagging for investigation",
          saga.getSagaId(),
          saga.getStatus(),
          saga.getCurrentStep(),
          saga.getUpdatedAt(),
          timeoutMs);
    }

    int flagged =
        sagaRepository.markFlaggedStuck(
            stuck.stream().map(Saga::getSagaId).toList(), IN_FLIGHT, threshold, Instant.now());
    log.warn("[stuck-detector] Flagged {} stuck saga(s) for investigation", flagged);
  }
}
