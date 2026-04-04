package com.example.common.exceptions;

import com.example.common.messaging.FailureType;

public final class WorkerExceptions {

  private WorkerExceptions() {}

  public static FailureType classify(Throwable t) {
    if (t instanceof TransientWorkerException) {
      return FailureType.TRANSIENT;
    }
    return FailureType.PERMANENT;
  }
}
