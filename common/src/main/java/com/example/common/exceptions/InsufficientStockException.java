package com.example.common.exceptions;

public class InsufficientStockException extends PermanentWorkerException {

  public InsufficientStockException(String productId, int requested, int available) {
    super(
        String.format(
            "Insufficient stock for %s: requested=%d, available=%d",
            productId, requested, available));
  }
}
