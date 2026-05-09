package com.example.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.exceptions.TransientWorkerException;
import com.example.common.interfaces.OrderWorker;
import com.example.order.domain.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService implements OrderWorker {

  private final OrderRepository orderRepository;

  @Override
  @Transactional
  public UUID createOrder(
      UUID sagaId, String customerId, List<StartCheckoutRequest.CheckoutItem> items) {
    log.info(
        "[order][saga={}] createOrder customerId={} items={}", sagaId, customerId, items.size());

    if ("cust_error".equals(customerId)) {
      throw new TransientWorkerException("Simulated DB failure for customer: " + customerId);
    }

    BigDecimal total =
        BigDecimal.valueOf(
            items.stream().mapToInt(StartCheckoutRequest.CheckoutItem::getQuantity).sum() * 49.99);

    UUID orderId = UUID.randomUUID();
    int inserted =
        orderRepository.insertIfAbsent(
            orderId, sagaId, customerId, "CREATED", total, Instant.now());
    if (inserted == 0) {
      UUID existingId = orderRepository.findBySagaId(sagaId).orElseThrow().getOrderId();
      log.info(
          "[order][saga={}] Order already exists — duplicate, returning existing orderId={}",
          sagaId,
          existingId);
      return existingId;
    }

    log.info("[order][saga={}] Order CREATED orderId={} total={}", sagaId, orderId, total);
    return orderId;
  }
}
