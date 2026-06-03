/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.jsonwebtoken.Jwts;
import io.quarkus.arc.Arc;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.taktx.Topics;
import io.taktx.client.TaktXClient;
import io.taktx.dto.CommandAuthMethod;
import io.taktx.dto.CommandTrustMetadataDTO;
import io.taktx.dto.CommandTrustVerificationResult;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.ConfigurationEventDTO.ConfigurationEventType;
import io.taktx.dto.Constants;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.generic.ClockProducer;
import io.taktx.engine.generic.TopologyProducer;
import io.taktx.engine.pi.testengine.BpmnTestEngine;
import io.taktx.engine.pi.testengine.ExternalTaskTriggerDeserializer;
import io.taktx.engine.pi.testengine.KafkaConsumerUtil;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningKeyRegistrar;
import io.taktx.security.SigningKeysStore;
import io.taktx.security.SigningKeysStoreHolder;
import io.taktx.serdes.ConfigurationProtoMapper;
import io.taktx.serdes.ProcessInstanceTriggerProtoMapper;
import io.taktx.serdes.SigningKeyProtoMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the two authorization paths and Ed25519 message-signing features.
 *
 * <p>Two signing paths exist in the engine:
 *
 * <ul>
 *   <li><b>JWT / RS256</b> ({@code tx-auth}) — used by Console, Platform, and Ingester for {@code
 *       StartCommandDTO}, {@code AbortTriggerDTO}, and {@code SetVariableTriggerDTO}.
 *   <li><b>Ed25519</b> ({@code tx-sig}) — used by workers, user-task handlers, and engine-internal
 *       commands (sub-process / call-activity starts). The deserializer verifies the signature;
 *       {@link io.taktx.engine.security.EngineAuthorizationService} only logs and passes it
 *       through.
 * </ul>
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
 *   <li>JWT path: valid JWT → command accepted, process instance started
 *   <li>JWT path: missing auth header → command rejected, no process instance started
 *   <li>JWT path: replayed {@code auditId} → second command rejected
 *   <li>Ed25519 inbound path: worker-signed external-task response → process completes
 *   <li>Ed25519 inbound path: engine-internal signed start (sub-process) → child process accepted
 *   <li>Ed25519 inbound path: revoked worker key → response dropped, process stays blocked
 *   <li>Outbound signing: instance-update records carry a valid {@code tx-sig} header
 *   <li>Outbound signing: external-task trigger records carry a valid {@code tx-sig}
 * </ul>
 */
@QuarkusTest
@TestProfile(SecurityTestProfile.class)
@QuarkusTestResource(value = SecurityTestConfigResource.class, restrictToAnnotatedClass = true)
@Tag("security-integration")
class SecurityIntegrationTest {

  private static final String TASK_SINGLE_PROCESS_ID = "task-single";
  private static final String USER_TASK_PROCESS_ID = "process-user-task";
  private static final String SERVICE_TASK_PROCESS_ID = "service-task-single";
  private static final String SERVICE_TASK_THEN_TIMER_PROCESS_ID = "service-task-then-timer";

  /**
   * The external-task type declared in {@code servicetask-single.bpmn} via {@code ="service-task"}.
   */
  private static final String SERVICE_TASK_TYPE = "service-task";

  private static final String SUB_PROCESS_ID = "subprocess-servicetask-single";
  private static final String WORKER_KEY_ID = "worker-test-key-1";
  private static final String REVOKED_KEY_ID = "revoked-worker-key-1";
  private static final String NAMESPACE = "default";
  private static final String ISSUER = "taktx-platform-service";

  // Shared helper so all topic names honour any future tenant-id without touching each call site.
  private static final java.util.Properties TEST_PROPS = new java.util.Properties();

  static {
    TEST_PROPS.put("taktx.engine.tenant-id", "test-tenant");
    TEST_PROPS.put("taktx.engine.namespace", NAMESPACE);
  }

  private static final io.taktx.util.TaktPropertiesHelper TOPIC_HELPER =
      new io.taktx.util.TaktPropertiesHelper(TEST_PROPS);

  private static String prefixed(String topicName) {
    return TOPIC_HELPER.getPrefixedTopicName(topicName);
  }

  /** Key ID embedded as {@code kid} in every JWT and stored in the signing-keys KTable. */
  private static final String PLATFORM_KID = SecurityTestConfigResource.PLATFORM_KID;

  /**
   * Private BpmnTestEngine for this test class only — NOT the shared singleton. Created
   * in @BeforeAll (after Quarkus+Kafka are up for the security profile).
   */
  private static BpmnTestEngine engine;

  /**
   * A second TaktXClient used only as a responder harness (topic discovery, process-definition
   * consumer). Signing is explicitly disabled so it never touches {@link
   * io.taktx.security.SigningServiceHolder} — that holder belongs to the engine's {@link
   * io.taktx.engine.security.MessageSigningService} and must not be overwritten.
   *
   * <p>Actual Ed25519 signing of worker responses is done by {@link #workerResponder}, which owns
   * an isolated {@link KafkaProducer} with a captured worker signing lambda.
   */
  private static TaktXClient workerClient;

  /**
   * Dedicated Kafka producer that signs every record with the worker's Ed25519 key. Uses an
   * isolated signing lambda captured at construction time — completely independent of {@link
   * io.taktx.security.SigningServiceHolder}.
   */
  private static WorkerResponder workerResponder;

  /** Base64-encoded private key for the revoked worker (signs but key is marked REVOKED). */
  private static String revokedWorkerPrivateKeyBase64;

  /** Base64-encoded private key for the active worker test signer. */
  private static String workerPrivateKeyBase64;

  /** Captures raw instance-update records (including headers) for signature assertions. */
  private static final ConcurrentLinkedQueue<ConsumerRecord<UUID, InstanceUpdateDTO>>
      rawInstanceUpdates = new ConcurrentLinkedQueue<>();

  /** Captures raw external-task trigger records (including headers) for signature assertions. */
  private static final ConcurrentLinkedQueue<ConsumerRecord<String, ExternalTaskTriggerDTO>>
      rawExternalTaskTriggers = new ConcurrentLinkedQueue<>();

  /**
   * KafkaConsumerUtil instances created in @BeforeAll / inside test methods. Stored so they can be
   * stopped in @AfterAll — without explicit cleanup the background consumer threads keep trying to
   * reconnect to the (now-restarted) broker, flooding logs throughout all subsequent tests.
   */
  private static KafkaConsumerUtil<UUID, InstanceUpdateDTO> rawInstanceUpdatesConsumer;

  private static KafkaConsumerUtil<String, ExternalTaskTriggerDTO> rawExternalTaskTriggersConsumer;

  @BeforeAll
  static void setupEngineAndConfig() {
    String bootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    SecurityTestConfigResource.refreshEngineSigningMetadata();

    // ── Generate worker key pairs ─────────────────────────────────────────────
    KeyPair workerKp = SigningKeyGenerator.generate();
    workerPrivateKeyBase64 = SigningKeyGenerator.encodePrivateKey(workerKp.getPrivate());
    String workerPublicKeyBase64 = SigningKeyGenerator.encodePublicKey(workerKp.getPublic());

    KeyPair revokedKp = SigningKeyGenerator.generate();
    revokedWorkerPrivateKeyBase64 = SigningKeyGenerator.encodePrivateKey(revokedKp.getPrivate());
    String revokedPublicKeyBase64 = SigningKeyGenerator.encodePublicKey(revokedKp.getPublic());

    // Publish the active worker public key so the engine KTable knows about it
    Properties signingKeyProps = new Properties();
    signingKeyProps.put("bootstrap.servers", bootstrapServers);
    signingKeyProps.put("taktx.engine.tenant-id", "test-tenant");
    signingKeyProps.put("taktx.engine.namespace", NAMESPACE);
    TaktXClient.publishSigningKey(signingKeyProps, WORKER_KEY_ID, workerPublicKeyBase64, "worker");

    // Publish the platform RSA public key under PLATFORM_KID so the engine can resolve it
    // from the KTable when validating JWTs (the kid header in every JWT points to this entry).
    // MUST use KeyRole.PLATFORM — the 5-arg overload defaults to KeyRole.CLIENT which causes
    // PublicKeyProvider.getKey() to reject it as "not trusted as a PLATFORM JWT issuer key".
    TaktXClient.publishSigningKey(
        signingKeyProps,
        PLATFORM_KID,
        SecurityTestConfigResource.rsaPublicKeyBase64,
        "platform",
        "RSA",
        io.taktx.dto.KeyRole.PLATFORM);

    // Publish the revoked key — status REVOKED so the engine rejects messages signed with it
    publishRevokedSigningKey(
        bootstrapServers,
        prefixed(Topics.SIGNING_KEYS_TOPIC.getTopicName()),
        REVOKED_KEY_ID,
        revokedPublicKeyBase64);

    // ── Create main BpmnTestEngine ────────────────────────────────────────────
    engine = new BpmnTestEngine(ClockProducer.FIXED_CLOCK);
    engine.init(SecurityTestConfigResource.enginePublicKeyBase64);

    // ── Create workerClient with no signing identity ──────────────────────────
    // A null-returning signing identity source prevents TaktXClientBuilder from calling
    // SigningServiceHolder.set(), which would overwrite the engine's MessageSigningService
    // registration and cause all outbound engine records to be signed with the wrong key.
    Properties workerProps = new Properties();
    workerProps.put("bootstrap.servers", bootstrapServers);
    workerProps.put("taktx.engine.tenant-id", "test-tenant");
    workerProps.put("taktx.engine.namespace", NAMESPACE);
    workerProps.put("taktx.external.task.consumer.threads", 1);
    workerClient =
        TaktXClient.newClientBuilder()
            .withProperties(workerProps)
            .withSigningIdentitySource(() -> null)
            .build();
    workerClient.start();

    // ── Create isolated WorkerResponder that signs with the worker key ────────
    // This producer owns its own signing lambda captured at construction time — it never reads
    // from SigningServiceHolder, so the engine's signing registration is never disturbed.
    workerResponder =
        new WorkerResponder(bootstrapServers, NAMESPACE, WORKER_KEY_ID, workerPrivateKeyBase64);

    // ── Subscribe to raw topics for header assertions ─────────────────────────
    rawInstanceUpdatesConsumer =
        new KafkaConsumerUtil<>(
            "security-test-raw-group",
            prefixed(Topics.INSTANCE_UPDATE_TOPIC.getTopicName()),
            io.taktx.util.TaktUUIDDeserializer.class.getName(),
            io.taktx.engine.pi.testengine.InstanceUpdateDeserializer.class.getName(),
            rawInstanceUpdates::add);

    // Publish a ConfigurationEventDTO to enable signing in the global config store
    publishSigningConfiguration();

    // ── Wait for engine to catch up with the published config and signing keys ─────────────────
    // On slow CI runners (GitHub Actions) the engine's Kafka Streams GlobalKTable and the
    // GlobalConfigStore may not have processed the records by the time tests start running.
    // Both are fire-and-forget Kafka writes above; we poll here until the engine reflects them.

    // 1. Wait for the engine's GlobalConfigStore to have signing enabled
    try (var configHandle = Arc.container().instance(GlobalConfigStore.class)) {
      GlobalConfigStore configStore = configHandle.get();
      await()
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(200))
          .until(() -> configStore.get() != null && configStore.get().isSigningEnabled());
    }

    // 2. Wait for the worker, platform, and engine signing keys to appear in the engine's KTable.
    // EngineSigningKeysHolder holds the lambda registered by EngineAuthorizationService that
    // delegates to the Kafka Streams state store — returning null for unknown keys.
    EngineSigningKeysHolder.KeyResolver keyResolver = EngineSigningKeysHolder.get();
    if (keyResolver != null) {
      await()
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(200))
          .until(() -> keyResolver.resolvePublicKey(WORKER_KEY_ID) != null);
      // Also wait for the platform RSA key — tests that issue JWTs cannot proceed until the
      // engine's KTable has processed this entry and PublicKeyProvider.getKey() can resolve it.
      await()
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(200))
          .until(() -> keyResolver.resolvePublicKey(PLATFORM_KID) != null);
      await()
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(200))
          .until(
              () -> keyResolver.resolvePublicKey(SecurityTestConfigResource.engineKeyId) != null);
    }
  }

  @AfterAll
  static void tearDownEngine() {
    if (rawInstanceUpdatesConsumer != null) {
      rawInstanceUpdatesConsumer.stop();
      rawInstanceUpdatesConsumer = null;
    }
    if (rawExternalTaskTriggersConsumer != null) {
      rawExternalTaskTriggersConsumer.stop();
      rawExternalTaskTriggersConsumer = null;
    }
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
    rawInstanceUpdates.clear();
    rawExternalTaskTriggers.clear();
    engine.reset();
  }

  // ── Authorization tests ────────────────────────────────────────────────────

  @Test
  void validJwt_commandAccepted_processInstanceStarted() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/task-single.bpmn");

    String auditId = UUID.randomUUID().toString();
    String jwt =
        buildJwt(
            "START",
            TASK_SINGLE_PROCESS_ID,
            -1,
            auditId,
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));

    UUID instanceId =
        startSignedProcess(TASK_SINGLE_PROCESS_ID, VariablesDTO.empty(), jwt);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(engine.getProcessInstanceMap()).containsKey(instanceId));
  }

  /**
   * A {@code StartCommandDTO} sent with no {@code tx-auth} and no {@code tx-sig} header must be
   * rejected by the engine — the process instance must never appear in the instance map.
   *
   * <p>We bypass {@link io.taktx.client.TaktXClient} deliberately: the client's {@code
   * processInstanceTriggerEmitter} wraps a {@link io.taktx.serdes.ProtoSigningSerializer} which
   * automatically attaches a {@code tx-sig} header via {@link
   * io.taktx.security.SigningServiceHolder}. Using the client would therefore exercise the Ed25519
   * path, not the missing-header path. Instead we produce the record with a plain {@link
   * KafkaProducer} using the raw protobuf serializer so no header is ever attached.
   */
  @Test
  void missingAuthHeader_commandRejected_noProcessInstanceStarted() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/task-single.bpmn");

    UUID instanceId = sendUnsignedStartCommand(TASK_SINGLE_PROCESS_ID);

    // The engine must reject the header-less command — instance must never appear in the map
    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(
            () -> assertThat(engine.getProcessInstanceMap()).doesNotContainKey(instanceId));
  }

  @Test
  void replayedAuditId_secondCommandRejected() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/task-single.bpmn");

    String auditId = UUID.randomUUID().toString(); // same auditId for both commands
    String jwt =
        buildJwt(
            "START",
            TASK_SINGLE_PROCESS_ID,
            -1,
            auditId,
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));

    // First command — should be accepted
    UUID firstId =
        startSignedProcess(TASK_SINGLE_PROCESS_ID, VariablesDTO.empty(), jwt);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(engine.getProcessInstanceMap()).containsKey(firstId));

    // Second command with the same auditId — should be rejected
    UUID secondId =
        startSignedProcess(TASK_SINGLE_PROCESS_ID, VariablesDTO.empty(), jwt);
    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(
            () -> assertThat(engine.getProcessInstanceMap()).doesNotContainKey(secondId));
  }

  @Test
  void malformedSignedStartCommand_rejected_streamContinuesForNextValidCommand()
      throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/task-single.bpmn");

    UUID malformedId =
        sendStartCommandWithSignatureHeader(TASK_SINGLE_PROCESS_ID, "malformed-header");
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(
            () -> assertThat(engine.getProcessInstanceMap()).doesNotContainKey(malformedId));

    String jwt =
        buildJwt(
            "START",
            TASK_SINGLE_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    UUID validId =
        startSignedProcess(TASK_SINGLE_PROCESS_ID, VariablesDTO.empty(), jwt);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(engine.getProcessInstanceMap()).containsKey(validId));
  }

  @Test
  void userTaskCompletion_withJwtAndSignature_completesProcess() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/usertask.bpmn");

    String startJwt =
        buildJwt(
            "START",
            USER_TASK_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    UUID instanceId = startSignedProcess(USER_TASK_PROCESS_ID, VariablesDTO.empty(), startJwt);

    engine.waitForNewProcessInstance().waitUntilUserTaskIsWaitingForResponse("UserTask_1");
    UserTaskTriggerDTO trigger = engine.getActiveUserTaskTrigger();

    String completionJwt =
        buildJwt(
            "USER_TASK_COMPLETE",
            engine.deployedProcessDefinition().getDefinitions().getRootProcess().getId(),
            engine.deployedProcessDefinition().getVersion(),
            UUID.randomUUID().toString(),
            "service-account://console/user-task",
            Date.from(Instant.now().plusSeconds(300)));

    sendSignedProcessInstanceTrigger(
        new UserTaskResponseTriggerDTO(
            trigger.getProcessInstanceId(),
            trigger.getElementInstanceIdPath(),
            new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null),
            VariablesDTO.of("approved", true)),
        WORKER_KEY_ID,
        workerPrivateKeyBase64,
        completionJwt);

    awaitProcessCompleted(instanceId);
  }

  @Test
  void externalTaskCompletion_withJwtAndSignature_completesProcess() throws IOException {
    engine
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_TYPE)
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn");

    UUID instanceId = startJwtProtectedProcessAndWaitForExternalTask();
    ExternalTaskTriggerDTO trigger = engine.getActiveExternalTaskTrigger(SERVICE_TASK_TYPE);

    String completionJwt =
        buildJwt(
            "EXTERNAL_TASK_COMPLETE",
            engine.deployedProcessDefinition().getDefinitions().getRootProcess().getId(),
            engine.deployedProcessDefinition().getVersion(),
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));

    sendSignedProcessInstanceTrigger(
        new ExternalTaskResponseTriggerDTO(
            trigger.getProcessInstanceId(),
            trigger.getElementInstanceIdPath(),
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
            VariablesDTO.of("var1", "ok")),
        WORKER_KEY_ID,
        workerPrivateKeyBase64,
        completionJwt);

    awaitProcessCompleted(instanceId);
  }

  @Test
  void externalTaskCompletion_wrongDefinitionClaim_rejected() throws IOException {
    engine
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_TYPE)
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn");

    UUID instanceId = startJwtProtectedProcessAndWaitForExternalTask();
    ExternalTaskTriggerDTO trigger = engine.getActiveExternalTaskTrigger(SERVICE_TASK_TYPE);

    String completionJwt =
        buildJwt(
            "EXTERNAL_TASK_COMPLETE",
            "wrong-definition",
            engine.deployedProcessDefinition().getVersion(),
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));

    sendSignedProcessInstanceTrigger(
        new ExternalTaskResponseTriggerDTO(
            trigger.getProcessInstanceId(),
            trigger.getElementInstanceIdPath(),
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
            VariablesDTO.empty()),
        WORKER_KEY_ID,
        workerPrivateKeyBase64,
        completionJwt);

    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(
            () ->
                assertThat(engine.getProcessInstanceMap().get(instanceId).getScope().getState())
                    .isNotEqualTo(ExecutionState.COMPLETED));
  }

  @Test
  void userTaskCompletion_wrongVersionClaim_rejected() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/usertask.bpmn");

    String startJwt =
        buildJwt(
            "START",
            USER_TASK_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    UUID instanceId = startSignedProcess(USER_TASK_PROCESS_ID, VariablesDTO.empty(), startJwt);

    engine.waitForNewProcessInstance().waitUntilUserTaskIsWaitingForResponse("UserTask_1");
    UserTaskTriggerDTO trigger = engine.getActiveUserTaskTrigger();

    String completionJwt =
        buildJwt(
            "USER_TASK_COMPLETE",
            engine.deployedProcessDefinition().getDefinitions().getRootProcess().getId(),
            engine.deployedProcessDefinition().getVersion() + 1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));

    sendSignedProcessInstanceTrigger(
        new UserTaskResponseTriggerDTO(
            trigger.getProcessInstanceId(),
            trigger.getElementInstanceIdPath(),
            new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null),
            VariablesDTO.empty()),
        WORKER_KEY_ID,
        workerPrivateKeyBase64,
        completionJwt);

    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(
            () ->
                assertThat(engine.getProcessInstanceMap().get(instanceId).getScope().getState())
                    .isNotEqualTo(ExecutionState.COMPLETED));
  }

  // ── Ed25519 inbound path ───────────────────────────────────────────────────

  /**
   * A worker response signed with a registered Ed25519 key must be accepted by the engine without a
   * JWT — the process should complete normally.
   *
   * <p>The {@link #workerClient} has {@code WORKER_KEY_ID} registered in the signing-keys KTable.
   * Its {@code ProtoSigningSerializer} attaches {@code tx-sig} to every {@code
   * ContinueFlowElementTriggerDTO}. The engine's protobuf deserializer verifies it via the KTable;
   * {@code EngineAuthorizationService} logs and passes it through.
   */
  @Test
  void workerEd25519SignedResponse_processCompletes() throws IOException {
    engine
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_TYPE)
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn");

    // Start via JWT (external commands need a JWT)
    String jwt =
        buildJwt(
            "START",
            SERVICE_TASK_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));

    // Wait until the instance is registered so activeProcessInstanceId is set, then wait
    // for the external-task trigger, then respond using workerResponder — it signs the
    // response with WORKER_KEY_ID Ed25519 via its own isolated KafkaProducer;
    // SigningServiceHolder is never touched so the engine's MessageSigningService
    // registration stays intact.
    startSignedProcess(SERVICE_TASK_PROCESS_ID, VariablesDTO.empty(), jwt);
    engine.waitForNewProcessInstance().waitForExternalTaskTrigger(SERVICE_TASK_TYPE);
    workerResponder.respondSuccess(
        engine.getActiveExternalTaskTrigger(SERVICE_TASK_TYPE), VariablesDTO.of("var1", "ok"));
    engine.waitUntilDone();

    engine
        .assertThatProcess()
        .hasInstantiatedElementWithId("ServiceTask_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  /**
   * Engine-internal {@code StartCommandDTO} messages (e.g. those emitted by the engine itself when
   * entering a sub-process scope) carry the engine's own {@code tx-sig}. The engine should accept
   * them via the Ed25519 passthrough path — no JWT required.
   *
   * <p>The sub-process BPMN causes the engine to emit a signed {@code StartFlowElementTriggerDTO}
   * for the sub-process scope. That trigger is consumed back on the {@code
   * process-instance-trigger} topic; the deserializer verifies the signature against the engine's
   * own key registered in the KTable, and {@code EngineAuthorizationService} accepts it.
   */
  @Test
  void engineInternalEd25519SignedStart_subProcessCompletes() throws IOException {
    engine
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/subprocess-servicetask-single.bpmn");

    String jwt =
        buildJwt(
            "START",
            SUB_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    startSignedProcess(SUB_PROCESS_ID, VariablesDTO.of("var1", "hello"), jwt);

    engine.waitForNewProcessInstance().waitForExternalTaskTrigger("servicetask");
    workerResponder.respondSuccess(
        engine.getActiveExternalTaskTrigger("servicetask"), VariablesDTO.of("var2", "world"));
    engine.waitUntilDone();

    engine
        .assertThatProcess()
        .hasInstantiatedElementWithId("SubProcess_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  /**
   * A worker response signed with a <em>revoked</em> key must be dropped by the engine's trigger
   * deserializer — the key is in the KTable with {@code REVOKED} status, so {@code
   * resolvePublicKeyFromKTable} returns {@code null} and deserialization fails. The process
   * instance should stay blocked (never complete).
   */
  @Test
  void revokedWorkerKey_responseDropped_processRemainsBlocked() throws IOException {
    engine
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_TYPE)
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn");

    String jwt =
        buildJwt(
            "START",
            SERVICE_TASK_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    UUID instanceId =
        startSignedProcess(SERVICE_TASK_PROCESS_ID, VariablesDTO.empty(), jwt);

    engine.waitForNewProcessInstance();
    engine.waitForExternalTaskTrigger(SERVICE_TASK_TYPE);

    // Produce a response signed with the REVOKED key directly via a raw producer so we bypass
    // the TaktXClient's signing path and control the keyId ourselves
    ExternalTaskTriggerDTO trigger = engine.getActiveExternalTaskTrigger(SERVICE_TASK_TYPE);
    sendRevokedWorkerResponse(trigger);

    // The process must NOT complete — the revoked-signed response is dropped by the deserializer
    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(
            () ->
                assertThat(engine.getProcessInstanceMap().get(instanceId).getScope().getState())
                    .as("Process must stay active — revoked response must be rejected")
                    .isNotEqualTo(ExecutionState.COMPLETED));
  }

  @Test
  void signingKeyPublishRotateAndRevokeLifecycle_worksEndToEnd() throws IOException {
    engine
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_TYPE)
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn");

    String bootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    String signingKeysTopic = prefixed(Topics.SIGNING_KEYS_TOPIC.getTopicName());

    KeyPair legacyKeyPair = SigningKeyGenerator.generate();
    String legacyKeyId = "rotation-old-" + UUID.randomUUID();
    String legacyPrivateKeyBase64 =
        SigningKeyGenerator.encodePrivateKey(legacyKeyPair.getPrivate());
    String legacyPublicKeyBase64 = SigningKeyGenerator.encodePublicKey(legacyKeyPair.getPublic());
    SigningKeyDTO legacyActiveKey =
        SigningKeyDTO.builder()
            .keyId(legacyKeyId)
            .publicKeyBase64(legacyPublicKeyBase64)
            .algorithm("Ed25519")
            .createdAt(Instant.now())
            .status(KeyStatus.ACTIVE)
            .owner("rotation-old-worker")
            .build();

    SigningKeyRegistrar.publishKeyWithStatus(bootstrapServers, signingKeysTopic, legacyActiveKey);
    awaitSigningKeyStatus(legacyKeyId, KeyStatus.ACTIVE);

    UUID publishedKeyInstanceId = startJwtProtectedProcessAndWaitForExternalTask();
    ExternalTaskTriggerDTO publishedKeyTrigger =
        engine.getActiveExternalTaskTrigger(SERVICE_TASK_TYPE);
    sendSignedExternalTaskResponse(publishedKeyTrigger, legacyKeyId, legacyPrivateKeyBase64);
    awaitProcessCompleted(publishedKeyInstanceId);

    KeyPair rotatedKeyPair = SigningKeyGenerator.generate();
    String rotatedKeyId = "rotation-new-" + UUID.randomUUID();
    String rotatedPrivateKeyBase64 =
        SigningKeyGenerator.encodePrivateKey(rotatedKeyPair.getPrivate());
    String rotatedPublicKeyBase64 = SigningKeyGenerator.encodePublicKey(rotatedKeyPair.getPublic());
    SigningKeyDTO rotatedActiveKey =
        SigningKeyDTO.builder()
            .keyId(rotatedKeyId)
            .publicKeyBase64(rotatedPublicKeyBase64)
            .algorithm("Ed25519")
            .createdAt(Instant.now())
            .status(KeyStatus.ACTIVE)
            .owner("rotation-new-worker")
            .build();

    SigningKeyRegistrar.publishKeyWithStatus(bootstrapServers, signingKeysTopic, rotatedActiveKey);
    awaitSigningKeyStatus(rotatedKeyId, KeyStatus.ACTIVE);

    SigningKeyDTO legacyTrustedKey =
        SigningKeyDTO.builder()
            .keyId(legacyActiveKey.getKeyId())
            .publicKeyBase64(legacyActiveKey.getPublicKeyBase64())
            .algorithm(legacyActiveKey.getAlgorithm())
            .createdAt(legacyActiveKey.getCreatedAt())
            .status(KeyStatus.TRUSTED)
            .owner(legacyActiveKey.getOwner())
            .build();
    SigningKeyRegistrar.publishKeyWithStatus(bootstrapServers, signingKeysTopic, legacyTrustedKey);
    awaitSigningKeyStatus(legacyKeyId, KeyStatus.TRUSTED);

    UUID trustedKeyInstanceId = startJwtProtectedProcessAndWaitForExternalTask();
    ExternalTaskTriggerDTO trustedKeyTrigger =
        engine.getActiveExternalTaskTrigger(SERVICE_TASK_TYPE);
    sendSignedExternalTaskResponse(trustedKeyTrigger, legacyKeyId, legacyPrivateKeyBase64);
    awaitProcessCompleted(trustedKeyInstanceId);

    UUID rotatedKeyInstanceId = startJwtProtectedProcessAndWaitForExternalTask();
    ExternalTaskTriggerDTO rotatedKeyTrigger =
        engine.getActiveExternalTaskTrigger(SERVICE_TASK_TYPE);
    sendSignedExternalTaskResponse(rotatedKeyTrigger, rotatedKeyId, rotatedPrivateKeyBase64);
    awaitProcessCompleted(rotatedKeyInstanceId);

    SigningKeyDTO legacyRevokedKey =
        SigningKeyDTO.builder()
            .keyId(legacyActiveKey.getKeyId())
            .publicKeyBase64(legacyActiveKey.getPublicKeyBase64())
            .algorithm(legacyActiveKey.getAlgorithm())
            .createdAt(legacyActiveKey.getCreatedAt())
            .status(KeyStatus.REVOKED)
            .owner(legacyActiveKey.getOwner())
            .build();
    SigningKeyRegistrar.publishKeyWithStatus(bootstrapServers, signingKeysTopic, legacyRevokedKey);
    awaitSigningKeyStatus(legacyKeyId, KeyStatus.REVOKED);

    UUID revokedKeyInstanceId = startJwtProtectedProcessAndWaitForExternalTask();
    ExternalTaskTriggerDTO revokedKeyTrigger =
        engine.getActiveExternalTaskTrigger(SERVICE_TASK_TYPE);
    sendSignedExternalTaskResponse(revokedKeyTrigger, legacyKeyId, legacyPrivateKeyBase64);

    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(
            () ->
                assertThat(
                        engine
                            .getProcessInstanceMap()
                            .get(revokedKeyInstanceId)
                            .getScope()
                            .getState())
                    .as("Process must stay active after legacy key revocation")
                    .isNotEqualTo(ExecutionState.COMPLETED));
  }

  // ── Outbound signing tests ─────────────────────────────────────────────────

  @Test
  void timerContinuationAfterWorkerResponse_isAttributedToEngine() throws IOException {
    engine
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_TYPE)
        .deployProcessDefinitionAndWait("/bpmn/servicetask-then-timer.bpmn");

    String jwt =
        buildJwt(
            "START",
            SERVICE_TASK_THEN_TIMER_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    UUID instanceId =
        startSignedProcess(SERVICE_TASK_THEN_TIMER_PROCESS_ID, VariablesDTO.empty(), jwt);

    engine.waitForNewProcessInstance();
    engine.waitForExternalTaskTrigger(SERVICE_TASK_TYPE);
    ExternalTaskTriggerDTO trigger = engine.getActiveExternalTaskTrigger(SERVICE_TASK_TYPE);
    workerResponder.respondSuccess(trigger, VariablesDTO.of("var1", "ok"));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        rawInstanceUpdates.stream()
                            .filter(currentRecord -> instanceId.equals(currentRecord.key()))
                            .map(ConsumerRecord::value)
                            .filter(
                                update ->
                                    update.getCurrentTrustMetadata() != null
                                        && WORKER_KEY_ID.equals(
                                            update.getCurrentTrustMetadata().getSignerKeyId()))
                            .findFirst())
                    .isPresent());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(engine.getScopeWithElementId(instanceId, "CatchEvent_1"))
                    .anyMatch(io.taktx.dto.FlowNodeInstanceDTO::isActive));
    engine.waitFor(Duration.ofMillis(500));

    rawInstanceUpdates.clear();

    engine.moveTimeForward(Duration.ofSeconds(2));

    AtomicReference<InstanceUpdateDTO> trustedUpdate = new AtomicReference<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              InstanceUpdateDTO found =
                  rawInstanceUpdates.stream()
                      .filter(currentRecord -> instanceId.equals(currentRecord.key()))
                      .map(ConsumerRecord::value)
                      .filter(
                          update ->
                              update.getCurrentTrustMetadata() != null
                                  && SecurityTestConfigResource.engineKeyId.equals(
                                      update.getCurrentTrustMetadata().getSignerKeyId()))
                      .findFirst()
                      .orElse(null);
              assertThat(found).isNotNull();
              trustedUpdate.set(found);
            });

    assertThat(trustedUpdate.get().getCurrentTrustMetadata())
        .extracting(
            CommandTrustMetadataDTO::getAuthMethod,
            CommandTrustMetadataDTO::getVerificationResult,
            CommandTrustMetadataDTO::getTrusted,
            CommandTrustMetadataDTO::getSignerKeyId,
            CommandTrustMetadataDTO::getSignerOwner)
        .containsExactly(
            CommandAuthMethod.ED25519,
            CommandTrustVerificationResult.ENGINE_SIGNED,
            true,
            SecurityTestConfigResource.engineKeyId,
            "engine");
    assertThat(trustedUpdate.get().getOriginTrustMetadata())
        .extracting(
            CommandTrustMetadataDTO::getAuthMethod,
            CommandTrustMetadataDTO::getVerificationResult,
            CommandTrustMetadataDTO::getTrusted,
            CommandTrustMetadataDTO::getSignerKeyId,
            CommandTrustMetadataDTO::getSignerOwner)
        .containsExactly(
            CommandAuthMethod.ED25519,
            CommandTrustVerificationResult.ENGINE_SIGNED,
            true,
            SecurityTestConfigResource.engineKeyId,
            "engine");
  }

  /**
   * External-task trigger records written by the engine to the worker topic must also carry the
   * {@code tx-sig} header signed with the engine's Ed25519 key.
   */
  @Test
  void externalTaskTriggerRecord_hasSignatureHeader() throws IOException {
    // Subscribe to the raw external-task trigger topic before starting the process
    String externalTaskTopic =
        prefixed(Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + SERVICE_TASK_TYPE);

    rawExternalTaskTriggersConsumer =
        new KafkaConsumerUtil<>(
            "security-test-ext-task-group-" + UUID.randomUUID(),
            externalTaskTopic,
            StringDeserializer.class.getName(),
            ExternalTaskTriggerDeserializer.class.getName(),
            rawExternalTaskTriggers::add);

    engine
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_TYPE)
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn");

    String jwt =
        buildJwt(
            "START",
            SERVICE_TASK_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    UUID instanceId =
        startSignedProcess(SERVICE_TASK_PROCESS_ID, VariablesDTO.empty(), jwt);

    engine.waitForNewProcessInstance();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              boolean hasSigned =
                  rawExternalTaskTriggers.stream()
                      .anyMatch(
                          r ->
                              r.value() != null
                                  && instanceId.equals(r.value().getProcessInstanceId())
                                  && r.headers().lastHeader(Constants.HEADER_ENGINE_SIGNATURE)
                                      != null);
              assertThat(hasSigned).as("External-task trigger record must carry tx-sig").isTrue();
            });

    // Also verify the signature is cryptographically valid
    ConsumerRecord<String, ExternalTaskTriggerDTO> signed =
        rawExternalTaskTriggers.stream()
            .filter(
                r ->
                    r.value() != null
                        && instanceId.equals(r.value().getProcessInstanceId())
                        && r.headers().lastHeader(Constants.HEADER_ENGINE_SIGNATURE) != null)
            .findFirst()
            .orElseThrow();

    Header sigHeader = signed.headers().lastHeader(Constants.HEADER_ENGINE_SIGNATURE);
    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    assertThat(dot).as("Signature header must contain a dot separator").isGreaterThan(0);
    byte[] sigBytes = Base64.getDecoder().decode(headerValue.substring(dot + 1));

    String enginePublicKeyBase64 = SecurityTestConfigResource.enginePublicKeyBase64;
    byte[] payloadBytes =
        TopologyProducer.EXTERNAL_TASK_TRIGGER_SERDE.serializer().serialize(null, signed.value());
    assertThat(Ed25519Service.verify(payloadBytes, sigBytes, enginePublicKeyBase64))
        .as("External-task trigger Ed25519 signature must be valid")
        .isTrue();
  }

  // ── Signing tests ──────────────────────────────────────────────────────────

  @Test
  void instanceUpdateRecord_hasSignatureHeader() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/task-single.bpmn");

    String jwt =
        buildJwt(
            "START",
            TASK_SINGLE_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    startSignedProcess(TASK_SINGLE_PROCESS_ID, VariablesDTO.empty(), jwt);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              boolean hasSignedRecord =
                  rawInstanceUpdates.stream()
                      .anyMatch(r -> r.headers().lastHeader("tx-sig") != null);
              assertThat(hasSignedRecord)
                  .as("At least one instance-update record must carry tx-sig")
                  .isTrue();
            });
  }

  @Test
  void instanceUpdateRecord_signatureIsVerifiable() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/task-single.bpmn");

    String jwt =
        buildJwt(
            "START",
            TASK_SINGLE_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    startSignedProcess(TASK_SINGLE_PROCESS_ID, VariablesDTO.empty(), jwt);

    String enginePublicKeyBase64 = SecurityTestConfigResource.enginePublicKeyBase64;

    AtomicReference<ConsumerRecord<UUID, InstanceUpdateDTO>> signedRecord = new AtomicReference<>();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              ConsumerRecord<UUID, InstanceUpdateDTO> found =
                  rawInstanceUpdates.stream()
                      .filter(r -> r.headers().lastHeader("tx-sig") != null)
                      .findFirst()
                      .orElse(null);
              assertThat(found).as("Signed instance-update record not yet received").isNotNull();
              signedRecord.set(found);
            });

    ConsumerRecord<UUID, InstanceUpdateDTO> consumerRecord = signedRecord.get();
    Header sigHeader = consumerRecord.headers().lastHeader("tx-sig");
    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);

    // Header format: "<keyId>.<base64(signature)>"
    int dotIndex = headerValue.indexOf('.');
    assertThat(dotIndex).as("Signature header must contain a dot separator").isGreaterThan(0);
    String keyId = headerValue.substring(0, dotIndex);
    byte[] signatureBytes = Base64.getDecoder().decode(headerValue.substring(dotIndex + 1));

    // Re-serialise the payload the same way the engine did to verify
    byte[] payloadBytes =
        TopologyProducer.INSTANCE_UPDATE_SERDE.serializer().serialize(null, consumerRecord.value());

    assertThat(keyId).isEqualTo(SecurityTestConfigResource.engineKeyId);
    assertThat(Ed25519Service.verify(payloadBytes, signatureBytes, enginePublicKeyBase64))
        .as("Ed25519 signature must be valid")
        .isTrue();
  }

  @Test
  void instanceUpdateRecord_containsJwtTrustMetadata() throws IOException {
    engine.deployProcessDefinitionAndWait("/bpmn/task-single.bpmn");

    String jwt =
        buildJwt(
            "START",
            TASK_SINGLE_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    startSignedProcess(TASK_SINGLE_PROCESS_ID, VariablesDTO.empty(), jwt);

    // In the security test profile both signingEnabled=true and engineRequiresAuthorization=true
    // are active from @BeforeAll. The BpmnTestEngine's TaktXClient does not own a signing
    // identity but uses the engine's global SigningServiceHolder (set by MessageSigningService),
    // so every outbound start command gets both a JWT header AND a tx-sig header.
    // EngineAuthorizationService combines them into JWT_AND_ED25519 — this is correct behaviour.
    AtomicReference<InstanceUpdateDTO> trustedUpdate = new AtomicReference<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              InstanceUpdateDTO found =
                  rawInstanceUpdates.stream()
                      .map(ConsumerRecord::value)
                      .filter(
                          update ->
                              update.getCurrentTrustMetadata() != null
                                  && (update.getCurrentTrustMetadata().getAuthMethod()
                                          == CommandAuthMethod.JWT
                                      || update.getCurrentTrustMetadata().getAuthMethod()
                                          == CommandAuthMethod.JWT_AND_ED25519))
                      .findFirst()
                      .orElse(null);
              assertThat(found).isNotNull();
              trustedUpdate.set(found);
            });

    // Accept both JWT (signing disabled / not yet active) and JWT_AND_ED25519 (signing active).
    // In the security profile signing is always enabled, so JWT_AND_ED25519 is expected.
    assertThat(trustedUpdate.get().getCurrentTrustMetadata().getAuthMethod())
        .as("authMethod must indicate JWT authorization was used")
        .isIn(CommandAuthMethod.JWT, CommandAuthMethod.JWT_AND_ED25519);
    assertThat(trustedUpdate.get().getCurrentTrustMetadata())
        .extracting(
            CommandTrustMetadataDTO::getVerificationResult,
            CommandTrustMetadataDTO::getTrusted,
            CommandTrustMetadataDTO::getUserId,
            CommandTrustMetadataDTO::getIssuer)
        .containsExactly(CommandTrustVerificationResult.JWT_AUTHORIZED, true, "user-1", ISSUER);
    assertThat(trustedUpdate.get().getOriginTrustMetadata())
        .isEqualTo(trustedUpdate.get().getCurrentTrustMetadata());
  }

  @Test
  void instanceUpdateRecord_containsWorkerSignerTrustMetadata() throws IOException {
    engine
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_TYPE)
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn");

    // Use a signed start command so the test is not sensitive to whether
    // signingEnabled=true has been replayed into the GlobalConfigStore yet.
    // The worker key is published in the signing-keys KTable and is accepted
    // by the engine regardless of the GlobalConfig replay race.
    String jwt =
        buildJwt(
            "START",
            SERVICE_TASK_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    sendSignedProcessInstanceTrigger(
        new io.taktx.dto.StartCommandDTO(
            UUID.randomUUID(),
            null,
            null,
            new io.taktx.dto.ProcessDefinitionKey(SERVICE_TASK_PROCESS_ID, -1),
            VariablesDTO.empty()),
        WORKER_KEY_ID,
        workerPrivateKeyBase64,
        jwt);

    engine.waitForNewProcessInstance();
    engine.waitForExternalTaskTrigger(SERVICE_TASK_TYPE);
    ExternalTaskTriggerDTO trigger = engine.getActiveExternalTaskTrigger(SERVICE_TASK_TYPE);
    workerResponder.respondSuccess(trigger, VariablesDTO.of("var1", "ok"));

    AtomicReference<InstanceUpdateDTO> trustedUpdate = new AtomicReference<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              InstanceUpdateDTO found =
                  rawInstanceUpdates.stream()
                      .map(ConsumerRecord::value)
                      .filter(
                          update ->
                              update.getCurrentTrustMetadata() != null
                                  && update.getCurrentTrustMetadata().getAuthMethod()
                                      == CommandAuthMethod.ED25519
                                  && WORKER_KEY_ID.equals(
                                      update.getCurrentTrustMetadata().getSignerKeyId()))
                      .findFirst()
                      .orElse(null);
              assertThat(found).isNotNull();
              trustedUpdate.set(found);
            });

    assertThat(trustedUpdate.get().getCurrentTrustMetadata())
        .extracting(
            CommandTrustMetadataDTO::getAuthMethod,
            CommandTrustMetadataDTO::getVerificationResult,
            CommandTrustMetadataDTO::getTrusted,
            CommandTrustMetadataDTO::getSignerKeyId,
            CommandTrustMetadataDTO::getSignerOwner)
        .containsExactly(
            CommandAuthMethod.ED25519,
            CommandTrustVerificationResult.SIGNATURE_VERIFIED,
            true,
            WORKER_KEY_ID,
            "worker");
    assertThat(trustedUpdate.get().getOriginTrustMetadata())
        .isEqualTo(trustedUpdate.get().getCurrentTrustMetadata());
  }

  @Test
  void workerSignedResponse_withForgedPayloadTrustMetadata_isIgnored() throws IOException {
    engine
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_TYPE)
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn");

    String jwt =
        buildJwt(
            "START",
            SERVICE_TASK_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-1",
            Date.from(Instant.now().plusSeconds(300)));
    startSignedProcess(SERVICE_TASK_PROCESS_ID, VariablesDTO.empty(), jwt);

    engine.waitForNewProcessInstance();
    engine.waitForExternalTaskTrigger(SERVICE_TASK_TYPE);
    ExternalTaskTriggerDTO trigger = engine.getActiveExternalTaskTrigger(SERVICE_TASK_TYPE);

    CommandTrustMetadataDTO forgedJwtMetadata =
        CommandTrustMetadataDTO.builder()
            .authMethod(CommandAuthMethod.JWT)
            .verificationResult(CommandTrustVerificationResult.JWT_AUTHORIZED)
            .trusted(true)
            .userId("mallory")
            .issuer("forged-issuer")
            .build();
    sendWorkerResponseWithInjectedTrustMetadata(trigger, forgedJwtMetadata, forgedJwtMetadata);

    AtomicReference<InstanceUpdateDTO> trustedUpdate = new AtomicReference<>();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              InstanceUpdateDTO found =
                  rawInstanceUpdates.stream()
                      .map(ConsumerRecord::value)
                      .filter(
                          update ->
                              update.getCurrentTrustMetadata() != null
                                  && WORKER_KEY_ID.equals(
                                      update.getCurrentTrustMetadata().getSignerKeyId()))
                      .findFirst()
                      .orElse(null);
              assertThat(found).isNotNull();
              trustedUpdate.set(found);
            });

    // The engine ignores the forged trust metadata injected into the DTO payload.
    // In the simplified model the origin JWT from the start command is propagated
    // forward, so the worker response produces JWT_AND_ED25519 — the JWT fields
    // reflect the verified origin context (user-1 / taktx-platform-service), NOT
    // the forged values (mallory / forged-issuer).
    CommandTrustMetadataDTO trustMeta = trustedUpdate.get().getCurrentTrustMetadata();
    assertThat(trustMeta.getAuthMethod()).isEqualTo(CommandAuthMethod.JWT_AND_ED25519);
    assertThat(trustMeta.getSignerKeyId()).isEqualTo(WORKER_KEY_ID);
    assertThat(trustMeta.getSignerOwner()).isEqualTo("worker");
    assertThat(trustMeta.getTrusted()).isTrue();
    // Forged values must NOT appear
    assertThat(trustMeta.getUserId()).as("forged userId must be absent").isNotEqualTo("mallory");
    assertThat(trustMeta.getIssuer())
        .as("forged issuer must be absent")
        .isNotEqualTo("forged-issuer");
    assertThat(trustedUpdate.get().getOriginTrustMetadata())
        .as("Forged payload trust metadata must not survive worker-signed command processing")
        .isEqualTo(trustMeta);
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
        .header()
        .keyId(PLATFORM_KID)
        .and()
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

  private UUID startJwtProtectedProcessAndWaitForExternalTask() {
    String jwt =
        buildJwt(
            "START",
            SERVICE_TASK_PROCESS_ID,
            -1,
            UUID.randomUUID().toString(),
            "user-rotation",
            Date.from(Instant.now().plusSeconds(300)));
    UUID instanceId = startSignedProcess(SERVICE_TASK_PROCESS_ID, VariablesDTO.empty(), jwt);
    engine.waitForNewProcessInstance();
    engine.waitForExternalTaskTrigger(SERVICE_TASK_TYPE);
    return instanceId;
  }

  /**
   * Sends a signed {@code StartCommandDTO} directly to the process-instance topic using the
   * published worker key. This avoids relying on the race between Kafka Streams replaying
   * {@code signingEnabled=true} from the GlobalConfigStore and the test sending its command —
   * the signed command is accepted by the engine regardless of replay timing.
   */
  private UUID startSignedProcess(
      String processDefinitionId, VariablesDTO variables, String jwt) {
    UUID instanceId = UUID.randomUUID();
    sendSignedProcessInstanceTrigger(
        new io.taktx.dto.StartCommandDTO(
            instanceId,
            null,
            null,
            new io.taktx.dto.ProcessDefinitionKey(processDefinitionId, -1),
            variables),
        WORKER_KEY_ID,
        workerPrivateKeyBase64,
        jwt);
    return instanceId;
  }

  private void sendSignedExternalTaskResponse(
      ExternalTaskTriggerDTO trigger, String keyId, String privateKeyBase64) {
    ExternalTaskResponseTriggerDTO response =
        new ExternalTaskResponseTriggerDTO(
            trigger.getProcessInstanceId(),
            trigger.getElementInstanceIdPath(),
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
            VariablesDTO.empty());
    sendSignedProcessInstanceTrigger(response, keyId, privateKeyBase64);
  }

  private void awaitProcessCompleted(UUID instanceId) {
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(engine.getProcessInstanceMap().get(instanceId).getScope().getState())
                    .isEqualTo(ExecutionState.COMPLETED));
  }

  private static void awaitSigningKeyStatus(String keyId, KeyStatus expectedStatus) {
    SigningKeysStore signingKeysStore = SigningKeysStoreHolder.get();
    if (signingKeysStore != null) {
      await()
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(200))
          .until(
              () -> {
                SigningKeyDTO dto = signingKeysStore.get(keyId);
                return dto != null && dto.getStatus() == expectedStatus;
              });
    }

    EngineSigningKeysHolder.KeyResolver keyResolver = EngineSigningKeysHolder.get();
    if (keyResolver != null) {
      await()
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(200))
          .until(
              () ->
                  expectedStatus == KeyStatus.REVOKED
                      ? keyResolver.resolvePublicKey(keyId) == null
                      : keyResolver.resolvePublicKey(keyId) != null);
    }
  }

  private void sendWorkerResponseWithInjectedTrustMetadata(
      ExternalTaskTriggerDTO trigger,
      CommandTrustMetadataDTO currentTrustMetadata,
      CommandTrustMetadataDTO originTrustMetadata) {
    ExternalTaskResponseTriggerDTO response =
        new ExternalTaskResponseTriggerDTO(
            trigger.getProcessInstanceId(),
            trigger.getElementInstanceIdPath(),
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
            VariablesDTO.empty());
    response.setCurrentTrustMetadata(currentTrustMetadata);
    response.setOriginTrustMetadata(originTrustMetadata);
    sendSignedProcessInstanceTrigger(response, WORKER_KEY_ID, workerPrivateKeyBase64);
  }

  private void sendSignedProcessInstanceTrigger(
      ProcessInstanceTriggerDTO trigger, String keyId, String privateKeyBase64) {
    sendSignedProcessInstanceTrigger(trigger, keyId, privateKeyBase64, null);
  }

  private void sendSignedProcessInstanceTrigger(
      ProcessInstanceTriggerDTO trigger,
      String keyId,
      String privateKeyBase64,
      String authorizationToken) {
    String topic = prefixed(Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName());
    byte[] payload;
    try (io.taktx.client.serdes.ProcessInstanceTriggerSerializer rawSerializer =
        new io.taktx.client.serdes.ProcessInstanceTriggerSerializer()) {
      payload = rawSerializer.serialize(topic, trigger);
    }

    byte[] signatureBytes = Ed25519Service.sign(payload, privateKeyBase64);
    String signatureHeaderValue = keyId + "." + Base64.getEncoder().encodeToString(signatureBytes);

    java.nio.ByteBuffer keyBuffer = java.nio.ByteBuffer.wrap(new byte[16]);
    keyBuffer.putLong(trigger.getProcessInstanceId().getMostSignificantBits());
    keyBuffer.putLong(trigger.getProcessInstanceId().getLeastSignificantBits());
    byte[] keyBytes = keyBuffer.array();

    java.util.Properties props = new java.util.Properties();
    props.put(
        "bootstrap.servers",
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class));
    props.put("acks", "all");

    try (KafkaProducer<byte[], byte[]> producer =
        new KafkaProducer<>(props, new ByteArraySerializer(), new ByteArraySerializer())) {
      ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topic, keyBytes, payload);
      record
          .headers()
          .add(
              Constants.HEADER_ENGINE_SIGNATURE,
              signatureHeaderValue.getBytes(StandardCharsets.UTF_8));
      if (authorizationToken != null && !authorizationToken.isBlank()) {
        record
            .headers()
            .add(
                Constants.HEADER_AUTHORIZATION,
                authorizationToken.getBytes(StandardCharsets.UTF_8));
      }
      producer.send(record);
      producer.flush();
    }
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

    try (KafkaProducer<String, byte[]> producer =
        new KafkaProducer<>(props, new StringSerializer(), new ByteArraySerializer())) {

      GlobalConfigurationDTO config =
          GlobalConfigurationDTO.builder()
              .signingEnabled(true)
              .engineRequiresAuthorization(true)
              .engineRequiresExternalTaskAuthorization(true)
              .engineRequiresUserTaskAuthorization(true)
              .trustedKeyIds(List.of(WORKER_KEY_ID))
              .build();

      ConfigurationEventDTO event =
          ConfigurationEventDTO.builder()
              .eventType(ConfigurationEventType.CONFIGURATION_UPDATE)
              .configuration(config)
              .timestamp(Instant.now())
              .build();

      producer.send(
          new ProducerRecord<>(
              prefixed(Topics.CONFIGURATION_TOPIC.getTopicName()),
              "config",
              ConfigurationProtoMapper.toProto(event).toByteArray()));
      producer.flush();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to publish signing configuration", e);
    }
  }

  /**
   * Publishes a {@link SigningKeyDTO} with {@code REVOKED} status to the signing-keys topic so the
   * engine's KTable refuses any message signed with that keyId.
   */
  private static void publishRevokedSigningKey(
      String bootstrapServers, String topic, String keyId, String publicKeyBase64) {
    SigningKeyDTO revokedDto =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64(publicKeyBase64)
            .algorithm("Ed25519")
            .createdAt(Instant.now())
            .status(KeyStatus.REVOKED)
            .owner("revoked-worker")
            .build();

    try {
      java.util.Properties props = new java.util.Properties();
      props.put("bootstrap.servers", bootstrapServers);
      props.put("acks", "all");
      try (KafkaProducer<String, byte[]> producer =
          new KafkaProducer<>(
              props,
              new StringSerializer(),
              new org.apache.kafka.common.serialization.ByteArraySerializer())) {
        producer.send(
            new ProducerRecord<>(
                topic, keyId, SigningKeyProtoMapper.toProto(revokedDto).toByteArray()));
        producer.flush();
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to publish revoked signing key", e);
    }
  }

  /**
   * Sends a {@code ExternalTaskResponseTriggerDTO} response signed with the REVOKED worker key,
   * bypassing the {@link TaktXClient} so we can control exactly which keyId is used.
   *
   * <p>The protobuf payload is serialised directly (no signing serializer wrapper) so only our
   * manually-crafted revoked-key signature header is attached.
   */
  private void sendRevokedWorkerResponse(ExternalTaskTriggerDTO trigger) {
    if (trigger == null) {
      throw new IllegalStateException("No active external task trigger found");
    }
    try {
      // Build a minimal success response using the public constructors
      ExternalTaskResponseResultDTO result =
          new ExternalTaskResponseResultDTO(ExternalTaskResponseType.SUCCESS, true, null, null, 0L);
      ExternalTaskResponseTriggerDTO response =
          new ExternalTaskResponseTriggerDTO(
              trigger.getProcessInstanceId(),
              trigger.getElementInstanceIdPath(),
              result,
              VariablesDTO.empty());

      byte[] payloadBytes = ProcessInstanceTriggerProtoMapper.toProto(response).toByteArray();

      // Sign with the REVOKED key
      byte[] sigBytes = Ed25519Service.sign(payloadBytes, revokedWorkerPrivateKeyBase64);
      String signatureHeaderValue =
          REVOKED_KEY_ID + "." + Base64.getEncoder().encodeToString(sigBytes);

      // Serialise the UUID key the same way TaktUUIDSerializer does (16 raw bytes)
      java.nio.ByteBuffer keyBuffer = java.nio.ByteBuffer.wrap(new byte[16]);
      keyBuffer.putLong(trigger.getProcessInstanceId().getMostSignificantBits());
      keyBuffer.putLong(trigger.getProcessInstanceId().getLeastSignificantBits());
      byte[] keyBytes = keyBuffer.array();

      String bootstrapServers =
          ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
      java.util.Properties props = new java.util.Properties();
      props.put("bootstrap.servers", bootstrapServers);
      String topic = prefixed(Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName());

      try (KafkaProducer<byte[], byte[]> producer =
          new KafkaProducer<>(
              props,
              new org.apache.kafka.common.serialization.ByteArraySerializer(),
              new org.apache.kafka.common.serialization.ByteArraySerializer())) {
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topic, keyBytes, payloadBytes);
        record
            .headers()
            .add(
                Constants.HEADER_ENGINE_SIGNATURE,
                signatureHeaderValue.getBytes(StandardCharsets.UTF_8));
        producer.send(record);
        producer.flush();
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to send revoked-worker response", e);
    }
  }

  /**
   * Sends a {@link io.taktx.dto.StartCommandDTO} with no headers to the {@code
   * process-instance-trigger} topic using a plain {@link KafkaProducer}, simulating an external
   * caller that omits both {@code tx-auth} and {@code tx-sig}.
   *
   * <p>We bypass {@link io.taktx.client.TaktXClient} deliberately: its {@code
   * processInstanceTriggerEmitter} wraps a {@link io.taktx.serdes.ProtoSigningSerializer} that
   * automatically attaches {@code tx-sig} via {@link io.taktx.security.SigningServiceHolder}. A
   * plain producer with no interceptors guarantees a completely header-free record.
   */
  private UUID sendUnsignedStartCommand(String processDefinitionId) {
    return sendStartCommandWithSignatureHeader(processDefinitionId, null);
  }

  private UUID sendStartCommandWithSignatureHeader(
      String processDefinitionId, String signatureHeaderValue) {
    UUID instanceId = UUID.randomUUID();
    io.taktx.dto.StartCommandDTO cmd =
        new io.taktx.dto.StartCommandDTO(
            instanceId,
            null,
            null,
            new io.taktx.dto.ProcessDefinitionKey(processDefinitionId, -1),
            VariablesDTO.empty());

    String bootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    String topic = prefixed(Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName());

    // Serialize via the no-Headers overload — SigningSerializer only signs in the Headers overload,
    // so calling serialize(topic, value) always returns plain protobuf bytes with no side-effects.
    byte[] payload;
    try (io.taktx.client.serdes.ProcessInstanceTriggerSerializer rawSerializer =
        new io.taktx.client.serdes.ProcessInstanceTriggerSerializer()) {
      payload = rawSerializer.serialize(topic, cmd);
    }

    // 16-byte big-endian UUID key — matches TaktUUIDSerializer
    java.nio.ByteBuffer keyBuffer = java.nio.ByteBuffer.wrap(new byte[16]);
    keyBuffer.putLong(instanceId.getMostSignificantBits());
    keyBuffer.putLong(instanceId.getLeastSignificantBits());
    byte[] keyBytes = keyBuffer.array();

    java.util.Properties props = new java.util.Properties();
    props.put("bootstrap.servers", bootstrapServers);
    props.put("acks", "all");
    try (KafkaProducer<byte[], byte[]> producer =
        new KafkaProducer<>(
            props,
            new org.apache.kafka.common.serialization.ByteArraySerializer(),
            new org.apache.kafka.common.serialization.ByteArraySerializer())) {
      ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topic, keyBytes, payload);
      if (signatureHeaderValue != null) {
        record
            .headers()
            .add(
                Constants.HEADER_ENGINE_SIGNATURE,
                signatureHeaderValue.getBytes(StandardCharsets.UTF_8));
      }
      producer.send(record);
      producer.flush();
    }
    return instanceId;
  }
}
