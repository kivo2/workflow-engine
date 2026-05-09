package com.example.inventory.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.inventory.domain.InventoryItem;
import com.example.inventory.domain.InventoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryDataSeeder implements ApplicationRunner {

  private final InventoryRepository inventoryRepository;

  @Override
  public void run(ApplicationArguments args) {
    if (inventoryRepository.count() > 0) return;

    seed("prod_1", "Widget A", 100);
    seed("prod_2", "Widget B", 3);
    seed("prod_3", "Widget C - Out of Stock", 0);

    log.info("[inventory] Seed data loaded — prod_1=100, prod_2=3, prod_3=0");
  }

  private void seed(String id, String name, int qty) {
    InventoryItem item = new InventoryItem();
    item.setProductId(id);
    item.setProductName(name);
    item.setAvailableQty(qty);
    item.setReservedQty(0);
    inventoryRepository.save(item);
  }
}
