package com.example.orchestrator.domain;

import java.time.Instant;
import java.util.UUID;

import com.example.common.enums.SagaStatus;
import com.example.common.enums.SagaStep;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "saga")
@Getter
@NoArgsConstructor
public class Saga {

  @Id
  @Column(name = "saga_id", updatable = false, nullable = false)
  private UUID sagaId;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SagaStatus status;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "current_step", nullable = false)
  private SagaStep currentStep;

  @Setter
  @Column(name = "customer_id", nullable = false)
  private String customerId;

  @Setter
  @Column(name = "order_id")
  private UUID orderId;

  @Setter
  @Column(name = "payment_id")
  private UUID paymentId;

  @Setter
  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Setter
  @Column(name = "max_retries", nullable = false)
  private int maxRetries = 3;

  @Setter
  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  @Setter
  @Column(name = "last_failure_reason")
  private String lastFailureReason;

  @Setter
  @Column(name = "error_message")
  private String errorMessage;

  @Setter
  @Column(name = "request_payload", columnDefinition = "TEXT")
  private String requestPayload;

  @Version
  @Column(nullable = false)
  private int version = 0;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "flagged_stuck_at")
  private Instant flaggedStuckAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  public static Saga start(String customerId) {
    Saga saga = new Saga();
    saga.sagaId = UUID.randomUUID();
    saga.customerId = customerId;
    saga.status = SagaStatus.STARTED;
    saga.currentStep = SagaStep.RESERVE_INVENTORY;
    return saga;
  }
}
