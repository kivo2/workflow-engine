package com.example.orchestrator.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.example.orchestrator.service.BackoffCalculator;

class BackoffCalculatorTest {

  private final BackoffCalculator calc = new BackoffCalculator(1000L, 30000L);

  @Test
  void firstRetryWaitsOneBaseDelay_notTwo() {
    assertThat(calc.exponentialDelayMs(0)).isEqualTo(1000L);
  }

  @Test
  void exponentialDelay_doublesEachAttempt() {
    assertThat(calc.exponentialDelayMs(0)).isEqualTo(1000L);
    assertThat(calc.exponentialDelayMs(1)).isEqualTo(2000L);
    assertThat(calc.exponentialDelayMs(2)).isEqualTo(4000L);
    assertThat(calc.exponentialDelayMs(3)).isEqualTo(8000L);
  }

  @Test
  void exponentialDelay_cappedAtMax() {
    assertThat(calc.exponentialDelayMs(20)).isEqualTo(30000L);
  }

  @Test
  void nextRetryAt_appliesEqualJitter_betweenHalfAndFull() {
    for (int i = 0; i < 500; i++) {
      long deltaMs = Duration.between(Instant.now(), calc.nextRetryAt(0)).toMillis();
      assertThat(deltaMs).isBetween(400L, 1000L);
    }
  }
}
