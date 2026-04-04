package com.example.common.interfaces;

import java.util.UUID;

import com.example.common.dto.StartCheckoutRequest;

public interface PaymentWorker {

  UUID authorizePayment(UUID sagaId, StartCheckoutRequest.PaymentMethod paymentMethod);
}
