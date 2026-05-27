package com.example.orchestrator.outbox;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.messaging.CheckoutCommand;
import com.example.common.messaging.CommandType;
import com.example.orchestrator.messaging.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandOutbox {

  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(UUID sagaId, CommandType commandType, StartCheckoutRequest request) {
    enqueueTo(KafkaTopics.CHECKOUT_COMMANDS, sagaId, commandType, request);
    log.info("[outbox] Enqueued {} command for saga={}", commandType, sagaId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueueToDeadLetter(
      UUID sagaId, CommandType commandType, StartCheckoutRequest request) {
    enqueueTo(KafkaTopics.CHECKOUT_COMMANDS_DLT, sagaId, commandType, request);
    log.warn("[outbox] Dead-lettered {} command for saga={}", commandType, sagaId);
  }

  private void enqueueTo(
      String topic, UUID sagaId, CommandType commandType, StartCheckoutRequest request) {
    CheckoutCommand command = new CheckoutCommand();
    command.setSagaId(sagaId);
    command.setCommandType(commandType);
    command.setItems(request.getItems());
    command.setPaymentMethod(request.getPaymentMethod());
    command.setCustomerId(request.getCustomerId());

    String payload;
    try {
      payload = objectMapper.writeValueAsString(command);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize command for saga=" + sagaId, e);
    }

    outboxRepository.save(OutboxMessage.pending(sagaId, topic, commandType.name(), payload));
  }
}
