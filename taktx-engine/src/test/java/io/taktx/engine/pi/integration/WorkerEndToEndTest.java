/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.pi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.jsonwebtoken.Jwts;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.taktx.client.ExternalTaskTriggerConsumer;
import io.taktx.client.TaktXClient;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.generic.ClockProducer;
import io.taktx.engine.pi.testengine.BpmnTestEngine;
import io.taktx.security.SigningKeyGenerator;
import java.io.IOException;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test that runs a real engine (embedded Quarkus + Kafka dev-services) alongside a real
 * worker {@link TaktXClient} that signs its responses with an Ed25519 key.
 *
 * <p>Flow per test:
 *
 * <ol>
 *   <li>A BPMN process is deployed containing one {@code ServiceTask}.
 *   <li>The process is started via a RS256 JWT (the normal "platform / console" path).
 *   <li>The engine publishes an external-task trigger signed with its own {@code test-key-1}.
 *   <li>A {@link WorkerResponder} (isolated KafkaProducer) receives the trigger from the {@code
 *       workerClient}'s consumer, then sends an Ed25519-signed response back to the engine using
 *       the worker's private key.
 *   <li>The engine verifies the worker signature and advances the process to completion.
 * </ol>
 *
 * <h3>Why signing is disabled on the TaktXClient</h3>
 *
 * <p>{@link io.taktx.security.SigningServiceHolder} is a process-wide static. Both the engine's
 * {@code MessageSigningService} and {@code TaktXClientBuilder} write to it. In a shared JVM the
 * last writer wins, so if the worker client enables signing it will overwrite the engine's
 * registration — causing every subsequent outbound engine record (instance-update, external-task
 * trigger) to be stamped with the worker keyId. Consumers that verify the signature will then fail
 * because the worker public key does not match the engine's signing bytes.
 *
 * <p>The solution mirrors {@link SecurityIntegrationTest}: the worker {@link TaktXClient} has
 * {@code taktx.signing.disabled=true} (so it never touches {@code SigningServiceHolder}), and a
 * separate {@link WorkerResponder} owns its own {@link
 * org.apache.kafka.clients.producer.KafkaProducer} with an {@code IsolatedSigningSerializer} that
 * captures the worker key at construction time.
 */
@QuarkusTest
@TestProfile(SecurityTestProfile.class)
@QuarkusTestResource(value = SecurityTestConfigResource.class, restrictToAnnotatedClass = true)
class WorkerEndToEndTest {

  private static final String SERVICE_TASK_PROCESS_ID = "service-task-single";
  private static final String SERVICE_TASK_TYPE = "service-task";
  private static final String WORKER_KEY_ID = "e2e-worker-key-1";
  private static final String NAMESPACE = "default";
  private static final String ISSUER = "taktx-platform-service";
  private static final String PLATFORM_KID = SecurityTestConfigResource.PLATFORM_KID;

  /** Observer/assertion helper — signing disabled, never touches SigningServiceHolder. */
  private static BpmnTestEngine engine;

  /**
   * Worker client with {@code taktx.signing.disabled=true}. Used only for:
   *
   * <ul>
   *   <li>Subscribing to the external-task trigger topic (receives engine-signed triggers).
   *   <li>Topic discovery / process-definition consumer.
   * </ul>
   *
   * It does NOT send signed responses — that is done by {@link #workerResponder}.
   */
  private static TaktXClient workerClient;

  /**
   * Isolated KafkaProducer that signs responses with the worker's Ed25519 key. Never reads from or
   * writes to {@link io.taktx.security.SigningServiceHolder}.
   */
  private static WorkerResponder workerResponder;

  /** Triggers collected by the worker's ExternalTaskTriggerConsumer. */
  private static final List<ExternalTaskTriggerDTO> receivedTriggers = new CopyOnWriteArrayList<>();

  @BeforeAll
  static void setUpEngineAndWorker() {
    String bootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);

    // ── Generate a fresh Ed25519 worker key pair ──────────────────────────────
    KeyPair workerKp = SigningKeyGenerator.generate();
    String workerPrivateKeyBase64 = SigningKeyGenerator.encodePrivateKey(workerKp.getPrivate());
    String workerPublicKeyBase64 = SigningKeyGenerator.encodePublicKey(workerKp.getPublic());

    // Publish the worker public key so the engine's KTable can verify worker responses
    TaktXClient.publishSigningKey(
        bootstrapServers, NAMESPACE, WORKER_KEY_ID, workerPublicKeyBase64, "e2e-worker");

    // Re-publish the platform RSA public key so the engine's KTable is updated with the key
    // generated by this run of SecurityTestConfigResource. When WorkerEndToEndTest runs after
    // SecurityIntegrationTest in the same JVM, SecurityTestConfigResource.start() is called again
    // (restrictToAnnotatedClass=true), generating NEW RSA keys. The engine (same Quarkus instance)
    // still has the OLD RSA public key in its KTable, so JWTs signed with the new private key would
    // be rejected. Re-publishing here forces the KTable to update before any JWT is sent.
    TaktXClient.publishSigningKey(
        bootstrapServers,
        NAMESPACE,
        PLATFORM_KID,
        SecurityTestConfigResource.rsaPublicKeyBase64,
        "platform");

    // ── BpmnTestEngine — observation only, signing disabled ──────────────────
    engine = new BpmnTestEngine(ClockProducer.FIXED_CLOCK);
    // Pass the engine public key so the BpmnTestEngine's JsonDeserializer can verify
    // engine-signed instance-update records.
    engine.init(SecurityTestConfigResource.ed25519PublicKeyBase64);

    // ── Worker TaktXClient — signing DISABLED ─────────────────────────────────
    // taktx.signing.disabled=true prevents TaktXClientBuilder from calling
    // SigningServiceHolder.set(), which would overwrite MessageSigningService's registration
    // and cause every outbound engine record to carry the worker keyId.
    Properties workerProps = new Properties();
    workerProps.put("bootstrap.servers", bootstrapServers);
    workerProps.put("taktx.engine.namespace", NAMESPACE);
    workerProps.put("taktx.external.task.consumer.threads", "1");
    workerProps.put("taktx.signing.disabled", "true");
    // Give the ExternalTaskTriggerJsonDeserializer the engine public key so it can verify
    // the engine's Ed25519 signature on incoming trigger records.
    workerProps.put(
        io.taktx.serdes.JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG,
        SecurityTestConfigResource.ed25519PublicKeyBase64);

    workerClient = TaktXClient.newClientBuilder().withProperties(workerProps).build();

    // Register consumer before start() so the subscription is in place immediately
    workerClient.registerExternalTaskConsumer(
        new ExternalTaskTriggerConsumer() {
          @Override
          public Set<String> getJobIds() {
            return Set.of(SERVICE_TASK_TYPE);
          }

          @Override
          public void acceptBatch(List<ExternalTaskTriggerDTO> batch) {
            receivedTriggers.addAll(batch);
          }
        },
        "e2e-worker-consumer-group-" + UUID.randomUUID());

    workerClient.start();

    // ── WorkerResponder — isolated KafkaProducer, signs with worker key ───────
    workerResponder =
        new WorkerResponder(bootstrapServers, NAMESPACE, WORKER_KEY_ID, workerPrivateKeyBase64);

    // Publish global config: signing enabled, both engine key and worker key trusted
    SecurityEndToEndHelper.publishSigningConfiguration(
        bootstrapServers, NAMESPACE, "test-key-1", WORKER_KEY_ID);
  }

  @AfterAll
  static void tearDown() {
    if (workerResponder != null) {
      workerResponder.close();
      workerResponder = null;
    }
    if (workerClient != null) {
      workerClient.stop();
      workerClient = null;
    }
    if (engine != null) {
      engine.close();
      engine = null;
    }
  }

  @BeforeEach
  void reset() {
    receivedTriggers.clear();
    engine.reset();
  }

  // ── Tests ──────────────────────────────────────────────────────────────────

  /**
   * Happy-path end-to-end:
   *
   * <ol>
   *   <li>JWT start command → engine processes, publishes signed external-task trigger.
   *   <li>Worker client receives the trigger (verifies engine signature via static key config).
   *   <li>WorkerResponder sends a signed success response to the engine.
   *   <li>Engine verifies the worker signature (via signing-keys KTable) and completes the process.
   * </ol>
   */
  @Test
  void realWorker_receivesSignedTrigger_respondsSuccess_processCompletes() throws IOException {
    engine
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_TYPE)
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn");

    String jwt = buildJwt(UUID.randomUUID().toString());
    engine.getTaktClient().startProcess(SERVICE_TASK_PROCESS_ID, -1, VariablesDTO.empty(), jwt);

    engine.waitForNewProcessInstance();
    engine.waitForExternalTaskTrigger(SERVICE_TASK_TYPE);

    // Wait until the real worker consumer receives the (engine-signed) trigger
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(receivedTriggers)
                    .as("Worker must receive at least one external-task trigger")
                    .isNotEmpty());

    ExternalTaskTriggerDTO trigger = receivedTriggers.getFirst();
    assertThat(trigger.getExternalTaskId()).isEqualTo(SERVICE_TASK_TYPE);

    // Respond via the isolated WorkerResponder — signs with the worker's Ed25519 key
    // without touching SigningServiceHolder
    workerResponder.respondSuccess(trigger, VariablesDTO.of("var1", "worker-result"));

    engine.waitUntilDone();

    engine
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("ServiceTask_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasVariableWithValue("MappedOutputVariable", "worker-result");
  }

  /**
   * Worker responds with an error (no retry). The engine registers an incident and the process
   * stays active rather than completing.
   */
  @Test
  void realWorker_errorResponse_engineRegistersIncident() throws IOException {
    engine
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_TYPE)
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn");

    String jwt = buildJwt(UUID.randomUUID().toString());
    engine.getTaktClient().startProcess(SERVICE_TASK_PROCESS_ID, -1, VariablesDTO.empty(), jwt);

    engine.waitForNewProcessInstance();
    engine.waitForExternalTaskTrigger(SERVICE_TASK_TYPE);

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(receivedTriggers).isNotEmpty());

    workerResponder.respondError(
        receivedTriggers.getFirst(), "SERVICE_ERROR", "Downstream failure");

    engine.waitUntilIncident();

    engine
        .assertThatProcess()
        .isStillActive()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_1");
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private String buildJwt(String auditId) {
    return Jwts.builder()
        .header()
        .keyId(PLATFORM_KID)
        .and()
        .subject("e2e-user")
        .issuer(ISSUER)
        .claim("action", "START")
        .claim("processDefinitionId", SERVICE_TASK_PROCESS_ID)
        .claim("version", -1)
        .claim("namespaceId", UUID.randomUUID().toString())
        .claim("auditId", auditId)
        .expiration(Date.from(Instant.now().plusSeconds(300)))
        .signWith(SecurityTestConfigResource.rsaPrivateKey)
        .compact();
  }
}
