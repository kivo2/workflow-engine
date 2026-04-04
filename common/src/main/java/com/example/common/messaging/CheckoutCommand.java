package com.example.common.messaging;

import java.util.List;
import java.util.UUID;

import com.example.common.dto.StartCheckoutRequest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutCommand {
  private UUID sagaId;
  private CommandType commandType;
  private List<StartCheckoutRequest.CheckoutItem> items;
  private StartCheckoutRequest.PaymentMethod paymentMethod;
  private String customerId;
}
