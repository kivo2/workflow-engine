package com.example.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.exceptions.TransientWorkerException;
import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private OrderRepository orderRepository;
  @InjectMocks private OrderService service;

  private UUID sagaId;

  @BeforeEach
  void setUp() {
    sagaId = UUID.randomUUID();
  }

  @Test
  void createOrder_happyPath_returnsOrderId() {
    when(orderRepository.insertIfAbsent(any(), eq(sagaId), any(), any(), any(), any()))
        .thenReturn(1);
    UUID orderId = service.createOrder(sagaId, "cust_123", List.of(item("prod_1", 2)));
    assertThat(orderId).isNotNull();
  }

  @Test
  void createOrder_transientFailure_throwsRetryableException() {
    assertThatThrownBy(() -> service.createOrder(sagaId, "cust_error", List.of(item("prod_1", 1))))
        .isInstanceOf(TransientWorkerException.class);
    verify(orderRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
  }

  @Test
  void createOrder_idempotency_returnsExistingOrderId() {
    Order existing = Order.create(sagaId, "cust_123", BigDecimal.TEN);
    when(orderRepository.insertIfAbsent(any(), eq(sagaId), any(), any(), any(), any()))
        .thenReturn(0);
    when(orderRepository.findBySagaId(sagaId)).thenReturn(Optional.of(existing));

    UUID result = service.createOrder(sagaId, "cust_123", List.of(item("prod_1", 1)));

    assertThat(result).isEqualTo(existing.getOrderId());
  }

  private StartCheckoutRequest.CheckoutItem item(String productId, int qty) {
    StartCheckoutRequest.CheckoutItem i = new StartCheckoutRequest.CheckoutItem();
    i.setProductId(productId);
    i.setQuantity(qty);
    return i;
  }
}
