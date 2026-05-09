package com.example.inventory.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.dto.StartCheckoutRequest;
import com.example.common.exceptions.InsufficientStockException;
import com.example.common.exceptions.TransientWorkerException;
import com.example.common.interfaces.InventoryWorker;
import com.example.inventory.domain.InventoryItem;
import com.example.inventory.domain.InventoryRepository;
import com.example.inventory.domain.InventoryReservationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService implements InventoryWorker {

  private final InventoryRepository inventoryRepository;
  private final InventoryReservationRepository reservationRepository;

  @Override
  @Transactional
  public void reserveInventory(UUID sagaId, List<StartCheckoutRequest.CheckoutItem> items) {
    log.info("[inventory][saga={}] reserveInventory requested items={}", sagaId, items.size());

    int inserted = reservationRepository.insertIfAbsent(sagaId, Instant.now());
    if (inserted == 0) {
      log.info("[inventory][saga={}] Already reserved — duplicate delivery, skipping", sagaId);
      return;
    }

    for (StartCheckoutRequest.CheckoutItem item : items) {
      if ("prod_error".equals(item.getProductId())) {
        throw new TransientWorkerException(
            "Simulated transient failure for product: " + item.getProductId());
      }
    }

    for (StartCheckoutRequest.CheckoutItem item : items) {
      InventoryItem inv =
          inventoryRepository
              .findByIdForUpdate(item.getProductId())
              .orElseThrow(
                  () -> new TransientWorkerException("Product not found: " + item.getProductId()));

      if (inv.getAvailableQty() < item.getQuantity()) {
        log.warn(
            "[inventory][saga={}] Insufficient stock for {}: requested={}, available={}",
            sagaId,
            item.getProductId(),
            item.getQuantity(),
            inv.getAvailableQty());
        throw new InsufficientStockException(
            item.getProductId(), item.getQuantity(), inv.getAvailableQty());
      }
    }

    for (StartCheckoutRequest.CheckoutItem item : items) {
      InventoryItem inv = inventoryRepository.findByIdForUpdate(item.getProductId()).orElseThrow();
      inv.setAvailableQty(inv.getAvailableQty() - item.getQuantity());
      inv.setReservedQty(inv.getReservedQty() + item.getQuantity());
      inventoryRepository.save(inv);
      log.info(
          "[inventory][saga={}] Reserved {} x {} (remaining={})",
          sagaId,
          item.getQuantity(),
          item.getProductId(),
          inv.getAvailableQty());
    }

    log.info("[inventory][saga={}] Reservation complete", sagaId);
  }

  @Override
  @Transactional
  public void releaseInventory(UUID sagaId, List<StartCheckoutRequest.CheckoutItem> items) {
    log.info("[inventory][saga={}] releaseInventory requested items={}", sagaId, items.size());

    int flipped = reservationRepository.markReleased(sagaId, Instant.now());
    if (flipped == 0) {
      log.info(
          "[inventory][saga={}] Already released (or never reserved) — skipping restore", sagaId);
      return;
    }

    for (StartCheckoutRequest.CheckoutItem item : items) {
      InventoryItem inv =
          inventoryRepository
              .findByIdForUpdate(item.getProductId())
              .orElseThrow(
                  () -> new TransientWorkerException("Product not found: " + item.getProductId()));
      inv.setAvailableQty(inv.getAvailableQty() + item.getQuantity());
      inv.setReservedQty(inv.getReservedQty() - item.getQuantity());
      inventoryRepository.save(inv);
      log.info(
          "[inventory][saga={}] Released {} x {} (available now {})",
          sagaId,
          item.getQuantity(),
          item.getProductId(),
          inv.getAvailableQty());
    }

    log.info("[inventory][saga={}] Release complete", sagaId);
  }
}
