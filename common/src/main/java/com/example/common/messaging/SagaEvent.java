package com.example.common.messaging;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "eventType",
    defaultImpl = UnknownSagaEvent.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = InventoryReservedEvent.class, name = "INVENTORY_RESERVED"),
  @JsonSubTypes.Type(value = PaymentAuthorizedEvent.class, name = "PAYMENT_AUTHORIZED"),
  @JsonSubTypes.Type(value = OrderCreatedEvent.class, name = "ORDER_CREATED"),
  @JsonSubTypes.Type(value = WorkerFailedEvent.class, name = "WORKER_FAILED"),
  @JsonSubTypes.Type(value = InventoryReleasedEvent.class, name = "INVENTORY_RELEASED")
})
public interface SagaEvent {
  UUID getSagaId();
}
