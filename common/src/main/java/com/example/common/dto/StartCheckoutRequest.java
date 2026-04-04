package com.example.common.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class StartCheckoutRequest {

  @NotBlank(message = "customerId is required")
  private String customerId;

  @NotEmpty(message = "items cannot be empty")
  @Valid
  private List<CheckoutItem> items;

  @NotNull(message = "paymentMethod is required")
  @Valid
  private PaymentMethod paymentMethod;

  @Data
  public static class CheckoutItem {
    @NotBlank(message = "productId is required")
    private String productId;

    @Positive(message = "quantity must be greater than zero")
    private int quantity;
  }

  @Data
  public static class PaymentMethod {
    @NotBlank(message = "payment type is required")
    private String type;

    @NotBlank(message = "payment token is required")
    private String token;
  }
}
