package com.example.orchestrator.outbox;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.messaging.CheckoutCommand;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

  private final OutboxRepository outboxRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @Value("${outbox.relay.enabled:true}")
  private boolean enabled;

  @Value("${outbox.relay.batch-size:100}")
  private int batchSize;

  @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:500}")
  @Transactional
  public void publishPending() {
    if (!enabled) {
      return;
    }

    List<OutboxMessage> batch =
        outboxRepository.findByStatusOrderByIdAsc(
            OutboxStatus.PENDING, PageRequest.of(0, batchSize));
    if (batch.isEmpty()) {
      return;
    }

    log.info("[outbox-relay] Publishing {} pending message(s)", batch.size());

    for (OutboxMessage msg : batch) {
      try {
        CheckoutCommand command = objectMapper.readValue(msg.getPayload(), CheckoutCommand.class);
        kafkaTemplate
            .send(msg.getTopic(), msg.getSagaId().toString(), command)
            .get(5, TimeUnit.SECONDS);
        msg.markPublished();
        log.info(
            "[outbox-relay] Published {} for saga={} (outboxId={})",
            msg.getCommandType(),
            msg.getSagaId(),
            msg.getId());
      } catch (Exception e) {
        log.error(
            "[outbox-relay] Failed to publish outboxId={} saga={}, will retry",
            msg.getId(),
            msg.getSagaId(),
            e);
      }
    }
  }
}
