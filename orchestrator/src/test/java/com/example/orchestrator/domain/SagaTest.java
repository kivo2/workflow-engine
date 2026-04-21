package com.example.orchestrator.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.common.enums.SagaStatus;
import com.example.common.enums.SagaStep;

class SagaTest {

  @Test
  void start_createsNewSagaInCorrectInitialState() {
    Saga saga = Saga.start("cust_123");

    assertThat(saga.getSagaId()).isNotNull();
    assertThat(saga.getCustomerId()).isEqualTo("cust_123");
    assertThat(saga.getStatus()).isEqualTo(SagaStatus.STARTED);
    assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.RESERVE_INVENTORY);
    assertThat(saga.getRetryCount()).isZero();
    assertThat(saga.getOrderId()).isNull();
    assertThat(saga.getErrorMessage()).isNull();
    assertThat(saga.getNextRetryAt()).isNull();
  }

  @Test
  void start_differentCallsProduceDifferentSagaIds() {
    Saga saga1 = Saga.start("cust_1");
    Saga saga2 = Saga.start("cust_2");

    assertThat(saga1.getSagaId()).isNotEqualTo(saga2.getSagaId());
  }
}
