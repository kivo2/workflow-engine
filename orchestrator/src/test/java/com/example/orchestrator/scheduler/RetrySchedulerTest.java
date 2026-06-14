package com.example.orchestrator.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.common.enums.SagaStatus;
import com.example.orchestrator.domain.Saga;
import com.example.orchestrator.domain.SagaRepository;
import com.example.orchestrator.service.CheckoutOrchestrator;

@ExtendWith(MockitoExtension.class)
class RetrySchedulerTest {

  @Mock private SagaRepository sagaRepository;
  @Mock private CheckoutOrchestrator orchestrator;
  @InjectMocks private RetryScheduler scheduler;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(scheduler, "batchSize", 100);
  }

  @Test
  void skipsSagaThatLostOptimisticLock_andContinuesWithTheRest() {
    Saga s1 = Saga.start("c1");
    Saga s2 = Saga.start("c2");
    UUID id1 = s1.getSagaId();
    UUID id2 = s2.getSagaId();
    when(sagaRepository.findByStatusAndNextRetryAtBefore(
            eq(SagaStatus.PENDING_RETRY), any(), any()))
        .thenReturn(List.of(s1, s2));
    doThrow(new ObjectOptimisticLockingFailureException(Saga.class, id1))
        .when(orchestrator)
        .refire(id1);

    scheduler.pollAndRefire();

    verify(orchestrator).refire(id1);
    verify(orchestrator).refire(id2);
  }

  @Test
  void emptyQueue_doesNothing() {
    when(sagaRepository.findByStatusAndNextRetryAtBefore(any(), any(), any()))
        .thenReturn(List.of());
    scheduler.pollAndRefire();
    verifyNoInteractions(orchestrator);
  }
}
