package com.example.common.dto;

import java.time.Instant;

import com.example.common.enums.SagaStatus;
import com.example.common.enums.SagaStep;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckoutResponse {
  private String sagaId;
  private SagaStatus status;
  private SagaStep currentStep;
  private String orderId;
  private String error;
  private int retryCount;
  private int maxRetries;
  private Instant nextRetryAt;
}
