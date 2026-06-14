package com.example.orchestrator.service;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BackoffCalculator {

  private final long baseDelayMs;
  private final long maxDelayMs;

  public BackoffCalculator(
      @Value("${retry.base-delay-ms:1000}") long baseDelayMs,
      @Value("${retry.max-delay-ms:30000}") long maxDelayMs) {
    this.baseDelayMs = baseDelayMs;
    this.maxDelayMs = maxDelayMs;
  }

  public long exponentialDelayMs(int retriesAttempted) {
    if (retriesAttempted >= 62) {
      return maxDelayMs;
    }
    long delay = baseDelayMs * (1L << retriesAttempted);
    return Math.min(delay, maxDelayMs);
  }

  public Instant nextRetryAt(int retriesAttempted) {
    long delay = exponentialDelayMs(retriesAttempted);
    long half = delay / 2;
    long jittered = half + ThreadLocalRandom.current().nextLong(0, (delay - half) + 1);
    return Instant.now().plusMillis(jittered);
  }
}
