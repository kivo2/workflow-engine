package com.example.common.exceptions;

import java.util.UUID;

public class DuplicateOrderException extends PermanentWorkerException {

  public DuplicateOrderException(UUID sagaId) {
    super(String.format("Order already exists in conflicting state for saga: %s", sagaId));
  }
}
