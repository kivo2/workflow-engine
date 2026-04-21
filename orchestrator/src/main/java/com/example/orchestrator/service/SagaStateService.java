package com.example.orchestrator.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.enums.SagaStatus;
import com.example.common.enums.SagaStep;
import com.example.orchestrator.domain.Saga;
import com.example.orchestrator.domain.SagaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaStateService {

  private final SagaRepository sagaRepository;

  @Transactional
  public Saga createSaga(String customerId, int maxRetries) {
    Saga saga = Saga.start(customerId);
    saga.setMaxRetries(maxRetries);
    saga.setStatus(SagaStatus.RUNNING);
    sagaRepository.save(saga);
    log.info("[saga={}] Created and committed", saga.getSagaId());
    return saga;
  }

  @Transactional
  public void advanceStep(UUID sagaId, SagaStep step) {
    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    saga.setCurrentStep(step);
    saga.setStatus(SagaStatus.RUNNING);
    sagaRepository.save(saga);
  }

  @Transactional
  public void recordPayment(UUID sagaId, UUID paymentId) {
    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    saga.setPaymentId(paymentId);
    sagaRepository.save(saga);
  }

  @Transactional
  public void completeSaga(UUID sagaId, UUID orderId) {
    Saga saga = sagaRepository.findById(sagaId).orElseThrow();
    saga.setOrderId(orderId);
    saga.setCurrentStep(SagaStep.COMPLETED);
    saga.setStatus(SagaStatus.COMPLETED);
    sagaRepository.save(saga);
  }
}
