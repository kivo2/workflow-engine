package com.example.common.exceptions;

public abstract class WorkerException extends RuntimeException {

  protected WorkerException(String message) {
    super(message);
  }

  protected WorkerException(String message, Throwable cause) {
    super(message, cause);
  }
}
