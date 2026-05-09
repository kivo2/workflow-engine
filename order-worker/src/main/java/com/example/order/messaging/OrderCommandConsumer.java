package com.example.order.messaging;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.example.common.enums.SagaStep;
import com.example.common.exceptions.WorkerExceptions;
import com.example.common.messaging.CheckoutCommand;
import com.example.common.messaging.CommandType;
import com.example.common.messaging.FailureType;
import com.example.common.messaging.OrderCreatedEvent;
import com.example.common.messaging.WorkerFailedEvent;
import com.example.order.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCommandConsumer {

  private final OrderService orderService;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  private static final String EVENTS_TOPIC = "checkout.events";

  @KafkaListener(
      topics = "checkout.commands",
      groupId = "order-group",
      containerFactory = "kafkaListenerContainerFactory")
  public void consumeCommand(
      @Payload CheckoutCommand command,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset,
      Acknowledgment ack) {

    if (command.getCommandType() != CommandType.CREATE_ORDER) {
      ack.acknowledge();
      return;
    }

    log.info(
        "[saga={}] Consumed CREATE_ORDER command from partition={} offset={}",
        command.getSagaId(),
        partition,
        offset);

    Object outcome;
    try {
      UUID orderId =
          orderService.createOrder(
              command.getSagaId(), command.getCustomerId(), command.getItems());
      outcome = new OrderCreatedEvent(command.getSagaId(), orderId, Instant.now());
      log.info("[saga={}] Created order", command.getSagaId());
    } catch (Exception e) {
      FailureType type = WorkerExceptions.classify(e);
      log.warn(
          "[saga={}] CREATE_ORDER failed ({}) — reporting {} failure",
          command.getSagaId(),
          e.getClass().getSimpleName(),
          type);
      outcome =
          new WorkerFailedEvent(
              command.getSagaId(), SagaStep.CREATE_ORDER, e.getMessage(), type, Instant.now());
    }

    publishOutcome(command.getSagaId(), outcome);
    ack.acknowledge();
  }

  private void publishOutcome(UUID sagaId, Object outcome) {
    try {
      kafkaTemplate.send(EVENTS_TOPIC, sagaId.toString(), outcome).get(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new RuntimeException("Failed to publish outcome for saga=" + sagaId, e);
    }
  }
}
