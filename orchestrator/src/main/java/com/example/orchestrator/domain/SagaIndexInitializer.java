package com.example.orchestrator.domain;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaIndexInitializer implements ApplicationRunner {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void run(ApplicationArguments args) {
    ensurePartialIndex(
        "idx_saga_pending_retry",
        "CREATE INDEX IF NOT EXISTS idx_saga_pending_retry"
            + " ON saga (next_retry_at) WHERE status = 'PENDING_RETRY'");

    ensurePartialIndex(
        "idx_saga_stuck",
        "CREATE INDEX IF NOT EXISTS idx_saga_stuck ON saga (updated_at)"
            + " WHERE flagged_stuck_at IS NULL AND status IN ('RUNNING', 'COMPENSATING')");
  }

  private void ensurePartialIndex(String name, String ddl) {
    try {
      jdbcTemplate.execute(ddl);
      log.info("[startup] Ensured partial index {}", name);
    } catch (Exception e) {
      log.warn(
          "[startup] Could not create partial index {} ({}); poll still works, just less"
              + " efficiently",
          name,
          e.getMessage());
    }
  }
}
