package com.example.common.interfaces;

import java.util.List;
import java.util.UUID;

import com.example.common.dto.StartCheckoutRequest;

public interface InventoryWorker {

  void reserveInventory(UUID sagaId, List<StartCheckoutRequest.CheckoutItem> items);

  void releaseInventory(UUID sagaId, List<StartCheckoutRequest.CheckoutItem> items);
}
