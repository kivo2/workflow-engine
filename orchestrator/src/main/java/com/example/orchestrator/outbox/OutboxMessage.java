package com.example.orchestrator.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor
public class OutboxMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "saga_id", nullable = false)
  private UUID sagaId;

  @Column(nullable = false)
  private String topic;

  @Column(name = "command_type", nullable = false)
  private String commandType;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OutboxStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  public static OutboxMessage pending(
      UUID sagaId, String topic, String commandType, String payload) {
    OutboxMessage m = new OutboxMessage();
    m.sagaId = sagaId;
    m.topic = topic;
    m.commandType = commandType;
    m.payload = payload;
    m.status = OutboxStatus.PENDING;
    m.createdAt = Instant.now();
    return m;
  }

  public void markPublished() {
    this.status = OutboxStatus.PUBLISHED;
    this.publishedAt = Instant.now();
  }
}
