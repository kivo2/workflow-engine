package com.example.orchestrator.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.example.common.messaging.InventoryReleasedEvent;
import com.example.common.messaging.InventoryReservedEvent;
import com.example.common.messaging.OrderCreatedEvent;
import com.example.common.messaging.PaymentAuthorizedEvent;
import com.example.common.messaging.SagaEvent;
import com.example.common.messaging.WorkerFailedEvent;
import com.example.orchestrator.service.CheckoutOrchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerEventConsumer {

  private final CheckoutOrchestrator orchestrator;

  @KafkaListener(
      topics = KafkaTopics.CHECKOUT_EVENTS,
      groupId = "orchestrator-group",
      containerFactory = "kafkaListenerContainerFactory")
  public void consumeSagaEvent(
      @Payload SagaEvent event,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset,
      Acknowledgment ack) {

    try {
      log.info(
          "[saga={}] Consumed {} from partition={} offset={}",
          event.getSagaId(),
          event.getClass().getSimpleName(),
          partition,
          offset);

      if (event instanceof InventoryReservedEvent e) {
        orchestrator.handleInventoryReserved(e.getSagaId(), e.getReservationId());
      } else if (event instanceof PaymentAuthorizedEvent e) {
        orchestrator.handlePaymentAuthorized(e.getSagaId(), e.getPaymentId());
      } else if (event instanceof OrderCreatedEvent e) {
        orchestrator.handleOrderCreated(e.getSagaId(), e.getOrderId());
      } else if (event instanceof WorkerFailedEvent e) {
        orchestrator.handleWorkerFailed(e);
      } else if (event instanceof InventoryReleasedEvent e) {
        orchestrator.handleInventoryReleased(e.getSagaId());
      } else {
        log.error(
            "[saga={}] Unhandled event type {} — skipping (wire it into the consumer)",
            event.getSagaId(),
            event.getClass().getSimpleName());
      }

      ack.acknowledge();

    } catch (Exception e) {
      log.error("[saga={}] Failed to process event", event.getSagaId(), e);
      throw e;
    }
  }
}
