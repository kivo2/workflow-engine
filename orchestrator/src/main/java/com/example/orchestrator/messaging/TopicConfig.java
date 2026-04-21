package com.example.orchestrator.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class TopicConfig {

  private static final int PARTITIONS = 1;
  private static final short REPLICAS = 1;

  @Bean
  public NewTopic checkoutCommands() {
    return TopicBuilder.name(KafkaTopics.CHECKOUT_COMMANDS)
        .partitions(PARTITIONS)
        .replicas(REPLICAS)
        .build();
  }

  @Bean
  public NewTopic checkoutEvents() {
    return TopicBuilder.name(KafkaTopics.CHECKOUT_EVENTS)
        .partitions(PARTITIONS)
        .replicas(REPLICAS)
        .build();
  }

  @Bean
  public NewTopic checkoutCommandsDlt() {
    return TopicBuilder.name(KafkaTopics.CHECKOUT_COMMANDS_DLT)
        .partitions(PARTITIONS)
        .replicas(REPLICAS)
        .build();
  }

  @Bean
  public NewTopic checkoutEventsDlt() {
    return TopicBuilder.name(KafkaTopics.CHECKOUT_EVENTS_DLT)
        .partitions(PARTITIONS)
        .replicas(REPLICAS)
        .build();
  }
}
