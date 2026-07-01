package com.example.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.exceptions.InsufficientStockException;
import com.example.inventory.domain.InventoryRepository;
import com.example.inventory.domain.InventoryReservationRepository;
import com.example.inventory.service.InventoryService;

@SpringBootTest
@ActiveProfiles("integrationtest")
@Testcontainers
class InventoryIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

  @Autowired private InventoryService service;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private InventoryReservationRepository reservationRepository;

  @Test
  void happyPath_prod1_reservesCorrectly() {
    UUID sagaId = UUID.randomUUID();
    var before = inventoryRepository.findById("prod_1").orElseThrow();
    int availBefore = before.getAvailableQty();
    int reservedBefore = before.getReservedQty();

    service.reserveInventory(sagaId, List.of(item("prod_1", 2)));

    var inv = inventoryRepository.findById("prod_1").orElseThrow();
    assertThat(inv.getAvailableQty()).isEqualTo(availBefore - 2);
    assertThat(inv.getReservedQty()).isEqualTo(reservedBefore + 2);
    assertThat(reservationRepository.findBySagaId(sagaId)).isPresent();
  }

  @Test
  void outOfStock_prod3_throwsAndLeavesNoReservation() {
    UUID sagaId = UUID.randomUUID();
    assertThatThrownBy(() -> service.reserveInventory(sagaId, List.of(item("prod_3", 1))))
        .isInstanceOf(InsufficientStockException.class);

    assertThat(inventoryRepository.findById("prod_3").orElseThrow().getAvailableQty()).isZero();
    assertThat(reservationRepository.findBySagaId(sagaId)).isEmpty();
  }

  @Test
  void duplicateDelivery_decrementsStockExactlyOnce() {
    UUID sagaId = UUID.randomUUID();
    int availBefore = inventoryRepository.findById("prod_1").orElseThrow().getAvailableQty();

    service.reserveInventory(sagaId, List.of(item("prod_1", 2)));
    service.reserveInventory(sagaId, List.of(item("prod_1", 2)));

    var inv = inventoryRepository.findById("prod_1").orElseThrow();
    assertThat(inv.getAvailableQty()).isEqualTo(availBefore - 2);
    assertThat(reservationRepository.findBySagaId(sagaId)).isPresent();
  }

  @Test
  void release_restoresStockAndMarksReservationReleased() {
    UUID sagaId = UUID.randomUUID();
    int availBefore = inventoryRepository.findById("prod_1").orElseThrow().getAvailableQty();
    int reservedBefore = inventoryRepository.findById("prod_1").orElseThrow().getReservedQty();

    service.reserveInventory(sagaId, List.of(item("prod_1", 2)));
    service.releaseInventory(sagaId, List.of(item("prod_1", 2)));

    var inv = inventoryRepository.findById("prod_1").orElseThrow();
    assertThat(inv.getAvailableQty()).isEqualTo(availBefore);
    assertThat(inv.getReservedQty()).isEqualTo(reservedBefore);
    assertThat(reservationRepository.findBySagaId(sagaId).orElseThrow().getReleasedAt())
        .isNotNull();
  }

  @Test
  void duplicateRelease_restoresStockExactlyOnce() {
    UUID sagaId = UUID.randomUUID();
    int availBefore = inventoryRepository.findById("prod_1").orElseThrow().getAvailableQty();

    service.reserveInventory(sagaId, List.of(item("prod_1", 2)));
    service.releaseInventory(sagaId, List.of(item("prod_1", 2)));
    service.releaseInventory(sagaId, List.of(item("prod_1", 2)));

    assertThat(inventoryRepository.findById("prod_1").orElseThrow().getAvailableQty())
        .isEqualTo(availBefore);
  }

  @Test
  void releaseWithoutReservation_isNoOp() {
    UUID sagaId = UUID.randomUUID();
    int availBefore = inventoryRepository.findById("prod_1").orElseThrow().getAvailableQty();

    service.releaseInventory(sagaId, List.of(item("prod_1", 2)));

    assertThat(inventoryRepository.findById("prod_1").orElseThrow().getAvailableQty())
        .isEqualTo(availBefore);
    assertThat(reservationRepository.findBySagaId(sagaId)).isEmpty();
  }

  private StartCheckoutRequest.CheckoutItem item(String productId, int qty) {
    StartCheckoutRequest.CheckoutItem item = new StartCheckoutRequest.CheckoutItem();
    item.setProductId(productId);
    item.setQuantity(qty);
    return item;
  }
}
