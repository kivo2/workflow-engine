package com.example.orchestrator;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.common.enums.SagaStatus;
import com.example.common.enums.SagaStep;
import com.example.common.messaging.FailureType;
import com.example.common.messaging.InventoryReleasedEvent;
import com.example.common.messaging.WorkerFailedEvent;
import com.example.orchestrator.domain.Saga;
import com.example.orchestrator.domain.SagaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
@EmbeddedKafka(
    partitions = 1,
    topics = {"checkout.commands", "checkout.events"},
    brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"})
class CheckoutIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
  @Autowired private SagaRepository sagaRepository;

  private static final String HAPPY_PATH_REQUEST =
      """
      {
        "customerId": "cust_123",
        "items": [{"productId": "prod_1", "quantity": 1}],
        "paymentMethod": {"type": "card", "token": "tok_abc"}
      }
      """;

  private static final String DECLINED_REQUEST =
      """
      {
        "customerId": "cust_123",
        "items": [{"productId": "prod_1", "quantity": 1}],
        "paymentMethod": {"type": "card", "token": "tok_declined"}
      }
      """;

  private static final String TRANSIENT_FAILURE_REQUEST =
      """
      {
        "customerId": "cust_123",
        "items": [{"productId": "prod_1", "quantity": 1}],
        "paymentMethod": {"type": "card", "token": "tok_error"}
      }
      """;

  @Test
  void happyPath_checkoutReturns202Accepted() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/checkout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(HAPPY_PATH_REQUEST))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.sagaId").isNotEmpty())
            .andExpect(jsonPath("$.status").value(SagaStatus.RUNNING.name()))
            .andExpect(jsonPath("$.currentStep").value(SagaStep.RESERVE_INVENTORY.name()))
            .andExpect(jsonPath("$.orderId").isEmpty())
            .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    String sagaId = json.get("sagaId").asText();

    mockMvc
        .perform(get("/checkout/" + sagaId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(SagaStatus.RUNNING.name()));
  }

  @Test
  void paymentDeclined_startsWorkflowAsync() throws Exception {
    mockMvc
        .perform(
            post("/checkout").contentType(MediaType.APPLICATION_JSON).content(DECLINED_REQUEST))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value(SagaStatus.RUNNING.name()));
  }

  @Test
  void transientFailure_startsWorkflowAsync() throws Exception {
    mockMvc
        .perform(
            post("/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TRANSIENT_FAILURE_REQUEST))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value(SagaStatus.RUNNING.name()));
  }

  @Test
  void invalidRequest_returns400WithFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":"","items":[],"paymentMethod":null}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messages").isArray());
  }

  @Test
  void unknownSagaId_returns404() throws Exception {
    mockMvc
        .perform(get("/checkout/00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isNotFound());
  }

  @Test
  void workerFailedEvent_overTheWire_movesSagaToPendingRetry() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/checkout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(HAPPY_PATH_REQUEST))
            .andExpect(status().isAccepted())
            .andReturn();
    String sagaId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("sagaId").asText();

    WorkerFailedEvent failure =
        new WorkerFailedEvent(
            UUID.fromString(sagaId),
            SagaStep.RESERVE_INVENTORY,
            "provider blip",
            FailureType.TRANSIENT,
            Instant.now());
    kafkaTemplate.send("checkout.events", sagaId, failure);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                mockMvc
                    .perform(get("/checkout/" + sagaId))
                    .andExpect(jsonPath("$.status").value(SagaStatus.PENDING_RETRY.name())));
  }

  @Test
  void unknownEventType_isSkippedGracefully_partitionKeepsMoving() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/checkout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(HAPPY_PATH_REQUEST))
            .andExpect(status().isAccepted())
            .andReturn();
    String sagaId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("sagaId").asText();

    kafkaTemplate.send(
        "checkout.events", "unknown", Map.of("eventType", "BOGUS_TYPE", "sagaId", sagaId));
    kafkaTemplate.send(
        "checkout.events",
        sagaId,
        new WorkerFailedEvent(
            UUID.fromString(sagaId),
            SagaStep.RESERVE_INVENTORY,
            "blip",
            FailureType.TRANSIENT,
            Instant.now()));

    await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                mockMvc
                    .perform(get("/checkout/" + sagaId))
                    .andExpect(jsonPath("$.status").value(SagaStatus.PENDING_RETRY.name())));
  }

  @Test
  void compensation_overTheWire_failThenReleaseReachesCompensated() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/checkout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(HAPPY_PATH_REQUEST))
            .andExpect(status().isAccepted())
            .andReturn();
    String sagaId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("sagaId").asText();
    UUID id = UUID.fromString(sagaId);

    Saga saga = sagaRepository.findById(id).orElseThrow();
    saga.setCurrentStep(SagaStep.AUTHORIZE_PAYMENT);
    sagaRepository.save(saga);

    kafkaTemplate.send(
        "checkout.events",
        sagaId,
        new WorkerFailedEvent(
            id, SagaStep.AUTHORIZE_PAYMENT, "declined", FailureType.PERMANENT, Instant.now()));
    kafkaTemplate.send("checkout.events", sagaId, new InventoryReleasedEvent(id, Instant.now()));

    await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                mockMvc
                    .perform(get("/checkout/" + sagaId))
                    .andExpect(jsonPath("$.status").value(SagaStatus.COMPENSATED.name())));
  }

  @Test
  void healthEndpoint_isUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }
}
