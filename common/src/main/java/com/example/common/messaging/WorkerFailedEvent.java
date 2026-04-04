package com.example.common.messaging;

import java.time.Instant;
import java.util.UUID;

import com.example.common.enums.SagaStep;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkerFailedEvent implements SagaEvent {
  private UUID sagaId;
  private SagaStep failedStep;
  private String reason;
  private FailureType failureType;
  private Instant failedAt;
}
