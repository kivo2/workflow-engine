package com.example.common.exceptions;

public class PaymentDeclinedException extends PermanentWorkerException {

  public PaymentDeclinedException() {
    super("Payment declined by provider");
  }
}
