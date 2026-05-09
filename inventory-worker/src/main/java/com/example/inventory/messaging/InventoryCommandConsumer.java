package com.example.inventory.messaging;

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
import com.example.common.messaging.InventoryReleasedEvent;
import com.example.common.messaging.InventoryReservedEvent;
import com.example.common.messaging.WorkerFailedEvent;
import com.example.inventory.service.InventoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryCommandConsumer {

  private final InventoryService inventoryService;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  private static final String EVENTS_TOPIC = "checkout.events";

  @KafkaListener(
      topics = "checkout.commands",
      groupId = "inventory-group",
      containerFactory = "kafkaListenerContainerFactory")
  public void consumeCommand(
      @Payload CheckoutCommand command,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset,
      Acknowledgment ack) {

    CommandType type = command.getCommandType();
    if (type != CommandType.RESERVE_INVENTORY && type != CommandType.RELEASE_INVENTORY) {
      ack.acknowledge();
      return;
    }

    log.info(
        "[saga={}] Consumed {} command from partition={} offset={}",
        command.getSagaId(),
        type,
        partition,
        offset);

    Object outcome;
    try {
      outcome = handle(type, command);
    } catch (Exception e) {
      FailureType failureType = WorkerExceptions.classify(e);
      SagaStep step =
          type == CommandType.RESERVE_INVENTORY
              ? SagaStep.RESERVE_INVENTORY
              : SagaStep.RELEASE_INVENTORY;
      log.warn(
          "[saga={}] {} failed ({}) — reporting {} failure",
          command.getSagaId(),
          type,
          e.getClass().getSimpleName(),
          failureType);
      outcome =
          new WorkerFailedEvent(
              command.getSagaId(), step, e.getMessage(), failureType, Instant.now());
    }

    publishOutcome(command.getSagaId(), outcome);
    ack.acknowledge();
  }

  private Object handle(CommandType type, CheckoutCommand command) {
    UUID sagaId = command.getSagaId();
    if (type == CommandType.RESERVE_INVENTORY) {
      inventoryService.reserveInventory(sagaId, command.getItems());
      log.info("[saga={}] Reserved inventory", sagaId);
      return new InventoryReservedEvent(sagaId, UUID.randomUUID(), Instant.now());
    }
    inventoryService.releaseInventory(sagaId, command.getItems());
    log.info("[saga={}] Released inventory (compensation)", sagaId);
    return new InventoryReleasedEvent(sagaId, Instant.now());
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
