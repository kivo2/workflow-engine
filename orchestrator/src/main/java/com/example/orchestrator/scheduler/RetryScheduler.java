package com.example.orchestrator.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.common.enums.SagaStatus;
import com.example.orchestrator.domain.Saga;
import com.example.orchestrator.domain.SagaRepository;
import com.example.orchestrator.service.CheckoutOrchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetryScheduler {

  private final SagaRepository sagaRepository;
  private final CheckoutOrchestrator orchestrator;

  @Value("${retry.scheduler.auto-enabled:true}")
  private boolean autoEnabled;

  @Value("${retry.scheduler.batch-size:100}")
  private int batchSize;

  @Scheduled(fixedDelayString = "${retry.scheduler-interval-ms:5000}")
  public void scheduledPoll() {
    if (autoEnabled) {
      pollAndRefire();
    }
  }

  public void pollAndRefire() {
    List<Saga> ready =
        sagaRepository.findByStatusAndNextRetryAtBefore(
            SagaStatus.PENDING_RETRY,
            Instant.now(),
            PageRequest.of(0, batchSize, Sort.by("nextRetryAt")));
    if (ready.isEmpty()) {
      return;
    }

    log.info("[scheduler] {} saga(s) ready for retry", ready.size());
    for (Saga saga : ready) {
      UUID sagaId = saga.getSagaId();
      try {
        orchestrator.refire(sagaId);
      } catch (OptimisticLockingFailureException e) {
        log.info("[saga={}] Retry skipped — a concurrent update won; will re-evaluate", sagaId);
      } catch (Exception e) {
        log.error("[saga={}] Retry attempt errored", sagaId, e);
      }
    }
  }
}
