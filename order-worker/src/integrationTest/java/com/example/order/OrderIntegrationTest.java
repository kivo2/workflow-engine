package com.example.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.common.dto.StartCheckoutRequest;
import com.example.order.domain.OrderRepository;
import com.example.order.service.OrderService;

@SpringBootTest
@ActiveProfiles("integrationtest")
@Testcontainers
@Transactional
class OrderIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @Autowired private OrderService service;
  @Autowired private OrderRepository orderRepository;

  @Test
  void happyPath_createsOrderRecord() {
    UUID sagaId = UUID.randomUUID();
    UUID orderId = service.createOrder(sagaId, "cust_123", List.of(item("prod_1", 2)));

    assertThat(orderId).isNotNull();
    var order = orderRepository.findBySagaId(sagaId);
    assertThat(order).isPresent();
    assertThat(order.get().getStatus()).isEqualTo("CREATED");
    assertThat(order.get().getCustomerId()).isEqualTo("cust_123");
  }

  @Test
  void idempotency_secondCallReturnsSameOrderId() {
    UUID sagaId = UUID.randomUUID();
    UUID first = service.createOrder(sagaId, "cust_123", List.of(item("prod_1", 1)));
    UUID second = service.createOrder(sagaId, "cust_123", List.of(item("prod_1", 1)));
    assertThat(first).isEqualTo(second);
    assertThat(orderRepository.findAll()).hasSize(1);
  }

  private StartCheckoutRequest.CheckoutItem item(String productId, int qty) {
    StartCheckoutRequest.CheckoutItem i = new StartCheckoutRequest.CheckoutItem();
    i.setProductId(productId);
    i.setQuantity(qty);
    return i;
  }
}
