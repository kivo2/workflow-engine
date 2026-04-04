package com.example.common.enums;

public enum SagaStatus {
  STARTED,
  RUNNING,
  COMPLETED,
  FAILED,
  PENDING_RETRY,
  PERMANENTLY_FAILED,

  COMPENSATING,
  COMPENSATED
}
