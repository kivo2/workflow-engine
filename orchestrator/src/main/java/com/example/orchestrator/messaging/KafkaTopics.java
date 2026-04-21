package com.example.orchestrator.messaging;

public final class KafkaTopics {

  public static final String CHECKOUT_COMMANDS = "checkout.commands";

  public static final String CHECKOUT_COMMANDS_DLT = "checkout.commands.DLT";

  public static final String CHECKOUT_EVENTS = "checkout.events";

  public static final String CHECKOUT_EVENTS_DLT = "checkout.events.DLT";

  private KafkaTopics() {}
}
