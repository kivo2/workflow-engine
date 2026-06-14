package com.example.orchestrator.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.orchestrator.domain.Saga;
import com.example.orchestrator.domain.SagaRepository;

@ExtendWith(MockitoExtension.class)
class StuckSagaDetectorTest {

  @Mock private SagaRepository sagaRepository;
  @InjectMocks private StuckSagaDetector detector;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(detector, "timeoutMs", 60_000L);
    ReflectionTestUtils.setField(detector, "batchSize", 100);
  }

  @Test
  void stuckSagas_areFlaggedByIdViaBulkUpdate() {
    Saga s1 = Saga.start("c1");
    Saga s2 = Saga.start("c2");
    when(sagaRepository.findByStatusInAndUpdatedAtBeforeAndFlaggedStuckAtIsNull(
            any(), any(), any()))
        .thenReturn(List.of(s1, s2));

    detector.flagStuckSagas();

    verify(sagaRepository)
        .markFlaggedStuck(
            argThat(
                ids ->
                    ids.contains(s1.getSagaId())
                        && ids.contains(s2.getSagaId())
                        && ids.size() == 2),
            any(),
            any(Instant.class),
            any(Instant.class));
  }

  @Test
  void nothingStuck_doesNotWrite() {
    when(sagaRepository.findByStatusInAndUpdatedAtBeforeAndFlaggedStuckAtIsNull(
            any(), any(), any()))
        .thenReturn(List.of());

    detector.flagStuckSagas();

    verify(sagaRepository, never()).markFlaggedStuck(any(), any(), any(), any());
  }
}
