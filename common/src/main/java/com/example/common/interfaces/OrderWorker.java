package com.example.common.interfaces;

import java.util.List;
import java.util.UUID;

import com.example.common.dto.StartCheckoutRequest;

public interface OrderWorker {

  UUID createOrder(UUID sagaId, String customerId, List<StartCheckoutRequest.CheckoutItem> items);
}
