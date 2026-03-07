/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partisions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.pi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.jsonwebtoken.Jwts;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.taktx.Topics;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.ConfigurationEventDTO.ConfigurationEventType;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.generic.ClockProducer;
import io.taktx.engine.generic.TopologyProducer;
import io.taktx.engine.pi.testengine.BpmnTestEngine;
import io.taktx.engine.pi.testengine.KafkaConsumerUtil;
import io.taktx.security.Ed25519Service;
import io.taktx.security.SigningKeyGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the RBAC authorization and Ed25519 message-signing features.
 *
 * <p>These tests run against a real embedded Kafka broker (Quarkus dev-services) and a fully
 * started engine instance with authorization and signing enabled via {@link
 * SecurityTestConfigResource}.
 *
 * <p>This class intentionally does NOT use {@link
 * io.taktx.engine.pi.testengine.SingletonBpmnTestEngine}. The singleton is shared across all
 * default-profile tests and was initialised against the default Quarkus instance. Because {@link
 * SecurityTestProfile} forces a Quarkus restart, we create and manage our own {@link
 * BpmnTestEngine} here so that all Kafka connections are fresh against the restarted broker.
 *
 * <p>Test coverage:
 *
 * <ul>
 *   <li>Valid JWT → command accepted, process instance started
 *   <li>Missing JWT → command rejected, no process instance started
 *   <li>Replayed auditId → second command rejected
 *   <li>Instance-update records carry {@code X-TaktX-Signature} header
 *   <li>Signature on instance-update record is verifiable with the engine's Ed25519 public key
 * </ul>
 */
@QuarkusTest
@TestProfile(SecurityTestProfile.class)
@QuarkusTestResource(value = SecurityTestConfigResource.class, restrictToAnnotatedClass = true)
class SecurityIntegrationTest {

  private static final String PROCESS_DEFINITION_ID = "Process_0u5y4uz";
  private static final String NAMESPACE = "default";
  private static final String ISSUER = "taktx-platform-service";

  /**
   * Private BpmnTestEngine for this test class only — NOT the shared singleton. Created
   * in @BeforeAll (after Quarkus+Kafka are up for the security profile).
   */
  private static BpmnTestEngine engine;

  /** Captures raw instance-update records (including headers) for signature assertions. */
  private static final ConcurrentLinkedQueue<ConsumerRecord<UUID, InstanceUpdateDTO>>
      rawInstanceUpdates = new ConcurrentLinkedQueue<>();

  @BeforeAll
  static void setupEngineAndConfig() {
    // Create a fresh BpmnTestEngine connected to the security-profile Kafka/app instance.
    // ConfigProvider is safe here — @BeforeAll in @QuarkusTest runs after the app starts.
    engine = new BpmnTestEngine(ClockProducer.FIXED_CLOCK);
    engine.init();

    // Subscribe to instance-update topic to capture raw records with headers
    new KafkaConsumerUtil<>(
        "security-test-raw-group",
        NAMESPACE + "." + Topics.INSTANCE_UPDATE_TOPIC.getTopicName(),
        io.taktx.util.TaktUUIDDeserializer.class.getName(),
        io.taktx.engine.pi.testengine.InstanceUpdateDeserializer.class.getName(),
        rawInstanceUpdates::add);

    // Publish a ConfigurationEventDTO to enable signing in the global config store
    publishSigningConfiguration();
  }

  @AfterAll
  static void tearDownEngine() {
    if (engine != null) {
      engine.close();
      engine = null;
    }
  }

  @BeforeEach
  void reset() {
    rawInstanceUpdates.clear();
    engine.reset();
  }

  // ── Authorization tests ────────────────────────────────────────────────────

  @Test
  void validJwt_commandAccepted_processInstanceStarted() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/straight-through.bpmn");

    String auditId = UUID.randomUUID().toString();
    String jwt =
        buildJwt(
            "START",
            PROCESS_DEFINITION_ID,
            -1,
            auditId,
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));

    UUID instanceId =
        engine.getTaktClient().startProcess(PROCESS_DEFINITION_ID, -1, VariablesDTO.empty(), jwt);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(engine.getProcessInstanceMap()).containsKey(instanceId));
  }

  @Test
  void missingJwt_commandRejected_noProcessInstanceStarted() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/straight-through.bpmn");

    // Send command without an authorization token — engine should drop it
    UUID instanceId =
        engine.getTaktClient().startProcess(PROCESS_DEFINITION_ID, -1, VariablesDTO.empty(), null);

    // Wait briefly; the instance must NOT appear in the map
    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(
            () -> assertThat(engine.getProcessInstanceMap()).doesNotContainKey(instanceId));
  }

  @Test
  void replayedAuditId_secondCommandRejected() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/straight-through.bpmn");

    String auditId = UUID.randomUUID().toString(); // same auditId for both commands
    String jwt =
        buildJwt(
            "START",
            PROCESS_DEFINITION_ID,
            -1,
            auditId,
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));

    // First command — should be accepted
    UUID firstId =
        engine.getTaktClient().startProcess(PROCESS_DEFINITION_ID, -1, VariablesDTO.empty(), jwt);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(engine.getProcessInstanceMap()).containsKey(firstId));

    // Second command with the same auditId — should be rejected
    UUID secondId =
        engine.getTaktClient().startProcess(PROCESS_DEFINITION_ID, -1, VariablesDTO.empty(), jwt);
    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(
            () -> assertThat(engine.getProcessInstanceMap()).doesNotContainKey(secondId));
  }

  // ── Signing tests ──────────────────────────────────────────────────────────

  @Test
  void instanceUpdateRecord_hasSignatureHeader() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/straight-through.bpmn");

    String jwt =
        buildJwt(
            "START",
            PROCESS_DEFINITION_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    engine.getTaktClient().startProcess(PROCESS_DEFINITION_ID, -1, VariablesDTO.empty(), jwt);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              boolean hasSignedRecord =
                  rawInstanceUpdates.stream()
                      .anyMatch(r -> r.headers().lastHeader("X-TaktX-Signature") != null);
              assertThat(hasSignedRecord)
                  .as("At least one instance-update record must carry X-TaktX-Signature")
                  .isTrue();
            });
  }

  @Test
  void instanceUpdateRecord_signatureIsVerifiable() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/straight-through.bpmn");

    String jwt =
        buildJwt(
            "START",
            PROCESS_DEFINITION_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    engine.getTaktClient().startProcess(PROCESS_DEFINITION_ID, -1, VariablesDTO.empty(), jwt);

    String enginePublicKeyBase64 =
        SigningKeyGenerator.encodePublicKey(SecurityTestConfigResource.ed25519PublicKey);

    AtomicReference<ConsumerRecord<UUID, InstanceUpdateDTO>> signedRecord = new AtomicReference<>();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              ConsumerRecord<UUID, InstanceUpdateDTO> found =
                  rawInstanceUpdates.stream()
                      .filter(r -> r.headers().lastHeader("X-TaktX-Signature") != null)
                      .findFirst()
                      .orElse(null);
              assertThat(found).as("Signed instance-update record not yet received").isNotNull();
              signedRecord.set(found);
            });

    ConsumerRecord<UUID, InstanceUpdateDTO> record = signedRecord.get();
    Header sigHeader = record.headers().lastHeader("X-TaktX-Signature");
    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);

    // Header format: "<keyId>.<base64(signature)>"
    int dotIndex = headerValue.indexOf('.');
    assertThat(dotIndex).as("Signature header must contain a dot separator").isGreaterThan(0);
    String keyId = headerValue.substring(0, dotIndex);
    byte[] signatureBytes = Base64.getDecoder().decode(headerValue.substring(dotIndex + 1));

    // Re-serialise the payload the same way the engine did to verify
    byte[] payloadBytes =
        TopologyProducer.INSTANCE_UPDATE_SERDE.serializer().serialize(null, record.value());

    assertThat(keyId).isEqualTo("test-key-1");
    assertThat(Ed25519Service.verify(payloadBytes, signatureBytes, enginePublicKeyBase64))
        .as("Ed25519 signature must be valid")
        .isTrue();
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private String buildJwt(
      String action,
      String processDefinitionId,
      int version,
      String auditId,
      String userId,
      Date expiry) {
    return Jwts.builder()
        .subject(userId)
        .issuer(ISSUER)
        .claim("action", action)
        .claim("processDefinitionId", processDefinitionId)
        .claim("version", version)
        .claim("namespaceId", UUID.randomUUID().toString())
        .claim("auditId", auditId)
        .expiration(expiry)
        .signWith(SecurityTestConfigResource.rsaPrivateKey)
        .compact();
  }

  /**
   * Publishes a {@link ConfigurationEventDTO} to the namespaced {@code taktx-configuration} topic
   * so the engine's global config store has signing enabled with the test key active.
   */
  private static void publishSigningConfiguration() {
    String bootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);

    java.util.Properties props = new java.util.Properties();
    props.put("bootstrap.servers", bootstrapServers);

    try (KafkaProducer<String, ConfigurationEventDTO> producer =
        new KafkaProducer<>(
            props,
            new StringSerializer(),
            TopologyProducer.CONFIGURATION_EVENT_SERDE.serializer())) {

      GlobalConfigurationDTO config =
          GlobalConfigurationDTO.builder()
              .signingEnabled(true)
              .rbacEnabled(false)
              .activeKeyIds(List.of("test-key-1"))
              .build();

      ConfigurationEventDTO event =
          ConfigurationEventDTO.builder()
              .eventType(ConfigurationEventType.CONFIGURATION_UPDATE)
              .configuration(config)
              .timestamp(Instant.now())
              .build();

      producer.send(
          new ProducerRecord<>(
              NAMESPACE + "." + Topics.CONFIGURATION_TOPIC.getTopicName(), "config", event));
      producer.flush();
    }
  }
}
