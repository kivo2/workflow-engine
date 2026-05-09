package com.example.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
import com.example.common.exceptions.InsufficientStockException;
import com.example.inventory.domain.InventoryItem;
import com.example.inventory.domain.InventoryRepository;
import com.example.inventory.domain.InventoryReservationRepository;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

  @Mock private InventoryRepository inventoryRepository;
  @Mock private InventoryReservationRepository reservationRepository;
  @InjectMocks private InventoryService service;

  private UUID sagaId;
  private InventoryItem prod1;

  @BeforeEach
  void setUp() {
    sagaId = UUID.randomUUID();
    prod1 = new InventoryItem();
    prod1.setProductId("prod_1");
    prod1.setProductName("Widget A");
    prod1.setAvailableQty(10);
    prod1.setReservedQty(0);
  }

  @Test
  void reserveInventory_happyPath_reservesCorrectly() {
    when(reservationRepository.insertIfAbsent(eq(sagaId), any())).thenReturn(1);
    when(inventoryRepository.findByIdForUpdate("prod_1")).thenReturn(Optional.of(prod1));

    service.reserveInventory(sagaId, List.of(item("prod_1", 2)));

    assertThat(prod1.getAvailableQty()).isEqualTo(8);
    assertThat(prod1.getReservedQty()).isEqualTo(2);
    verify(reservationRepository).insertIfAbsent(eq(sagaId), any());
  }

  @Test
  void reserveInventory_insufficientStock_throwsException() {
    when(reservationRepository.insertIfAbsent(eq(sagaId), any())).thenReturn(1);
    prod1.setAvailableQty(1);
    when(inventoryRepository.findByIdForUpdate("prod_1")).thenReturn(Optional.of(prod1));

    assertThatThrownBy(() -> service.reserveInventory(sagaId, List.of(item("prod_1", 5))))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("prod_1");

    assertThat(prod1.getAvailableQty()).isEqualTo(1);
  }

  @Test
  void reserveInventory_idempotency_secondCallIsNoOp() {
    when(reservationRepository.insertIfAbsent(eq(sagaId), any())).thenReturn(0);

    service.reserveInventory(sagaId, List.of(item("prod_1", 2)));

    verify(inventoryRepository, never()).findByIdForUpdate(any());
  }

  @Test
  void reserveInventory_atomicity_nothingReservedIfAnyItemFails() {
    when(reservationRepository.insertIfAbsent(eq(sagaId), any())).thenReturn(1);
    InventoryItem prod2 = new InventoryItem();
    prod2.setProductId("prod_2");
    prod2.setAvailableQty(1);
    prod2.setReservedQty(0);

    when(inventoryRepository.findByIdForUpdate("prod_1")).thenReturn(Optional.of(prod1));
    when(inventoryRepository.findByIdForUpdate("prod_2")).thenReturn(Optional.of(prod2));

    assertThatThrownBy(
            () -> service.reserveInventory(sagaId, List.of(item("prod_1", 2), item("prod_2", 5))))
        .isInstanceOf(InsufficientStockException.class);

    assertThat(prod1.getAvailableQty()).isEqualTo(10);
  }

  private StartCheckoutRequest.CheckoutItem item(String productId, int qty) {
    StartCheckoutRequest.CheckoutItem item = new StartCheckoutRequest.CheckoutItem();
    item.setProductId(productId);
    item.setQuantity(qty);
    return item;
  }
}
