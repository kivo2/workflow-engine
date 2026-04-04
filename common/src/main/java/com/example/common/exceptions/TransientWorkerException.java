package com.example.common.exceptions;

public class TransientWorkerException extends WorkerException {

  public TransientWorkerException(String message) {
    super(message);
  }

  public TransientWorkerException(String message, Throwable cause) {
    super(message, cause);
  }
}
