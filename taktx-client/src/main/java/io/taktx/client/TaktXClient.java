/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.taktx.CleanupPolicy;
import io.taktx.client.annotation.Deployment;
import io.taktx.client.auth.AuthorizationTokenProvider;
import io.taktx.client.auth.OpenIdClientCredentialsTokenProvider;
import io.taktx.client.serdes.ProcessInstanceTriggerSerializer;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.ConfigurationEventDTO.ConfigurationEventType;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SignalDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EnvironmentWorkerSigningIdentitySource;
import io.taktx.security.FileSigningIdentitySource;
import io.taktx.security.GeneratedSigningIdentitySource;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningIdentitySource;
import io.taktx.security.SigningKeyRegistrar;
import io.taktx.security.SigningKeysStore;
import io.taktx.security.SigningKeysStoreHolder;
import io.taktx.security.SigningServiceHolder;
import io.taktx.serdes.SigningSerializer;
import io.taktx.topicmanagement.ExternalTaskTopicRequester;
import io.taktx.util.TaktPropertiesHelper;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;

/**
 * TaktXClient is the main entry point for interacting with the TaktX BPMN engine. It provides
 * methods to deploy process definitions, start process instances, send message events, and register
 * consumers for process definition updates, instance updates, external task triggers, and user task
 * triggers.
 */
public class TaktXClient {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(TaktXClient.class);
  private final ProcessDefinitionConsumer processDefinitionConsumer;
  static final String CONFIGURATION_RECORD_KEY = "config";
  private static final ObjectMapper CONFIG_OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private final ParameterResolverFactory parameterResolverFactory;
  private final ProcessInstanceResponder processInstanceResponder;
  private final ProcessDefinitionDeployer processDefinitionDeployer;
  private final DmnDefinitionDeployer dmnDefinitionDeployer;
  private final ProcessInstanceProducer processInstanceProducer;
  private final ProcessInstanceUpdateConsumer processInstanceUpdateConsumer;
  private final XmlByProcessDefinitionIdConsumer xmlByProcessDefinitionIdConsumer;
  private final MessageEventSender messageEventSender;
  private final SignalSender signalSender;
  private final ExternalTaskTriggerTopicConsumer externalTaskTriggerTopicConsumer;
  private final UserTaskTriggerTopicConsumer userTaskTriggerTopicConsumer;
  private final ExternalTaskTopicRequester externalTaskTopicRequester;
  private final ResultProcessorFactory resultProcessorFactory;
  private final TaktPropertiesHelper taktPropertiesHelper;
  private final SigningIdentitySource signingIdentitySource;
  private final @Nullable AuthorizationTokenProvider authorizationTokenProvider;

  /**
   * Optional base64-encoded RSA/SHA-256 registration signature for this worker's signing key.
   *
   * <p>Required when the engine operates in <em>anchored mode</em> ({@code
   * TAKTX_PLATFORM_PUBLIC_KEY} is set on the engine). Without it, the worker key published to
   * {@code taktx-signing-keys} will be rejected by {@link
   * io.taktx.security.AnchoredKeyTrustPolicy}.
   *
   * <p>Set via the {@code taktx.signing.registration-signature} property or the {@code
   * TAKTX_SIGNING_REGISTRATION_SIGNATURE} environment variable. The value is produced by the
   * platform root private key signing the canonical payload of the worker key:
   *
   * <pre>{@code keyId|publicKeyBase64|Ed25519|owner|CLIENT}</pre>
   *
   * See {@code scripts/generate_trust_anchor.sh --worker} for the complete workflow.
   */
  private final @Nullable String workerKeyRegistrationSignature;

  private SigningKeysStore signingKeysStore;
  private RuntimeConfigurationStore runtimeConfigurationStore;
  private volatile String publishedWorkerKeyId;
  private volatile String workerSigningRegistrationState = "uninitialized";

  private TaktXClient(
      TaktPropertiesHelper taktPropertiesHelper,
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter,
      ProcessInstanceResponder processInstanceResponder,
      ParameterResolverFactory parameterResolverFactory,
      ResultProcessorFactory resultProcessorFactory,
      SigningIdentitySource signingIdentitySource,
      @Nullable AuthorizationTokenProvider authorizationTokenProvider,
      @Nullable String workerKeyRegistrationSignature) {
    Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    this.taktPropertiesHelper = taktPropertiesHelper;
    this.signingIdentitySource = signingIdentitySource;
    this.authorizationTokenProvider = authorizationTokenProvider;
    this.workerKeyRegistrationSignature = workerKeyRegistrationSignature;
    this.externalTaskTopicRequester = new ExternalTaskTopicRequester(taktPropertiesHelper);
    this.parameterResolverFactory = parameterResolverFactory;
    this.resultProcessorFactory = resultProcessorFactory;
    this.processDefinitionConsumer = new ProcessDefinitionConsumer(taktPropertiesHelper, executor);
    this.xmlByProcessDefinitionIdConsumer =
        new XmlByProcessDefinitionIdConsumer(taktPropertiesHelper, executor);
    this.processDefinitionDeployer = new ProcessDefinitionDeployer(taktPropertiesHelper);
    this.dmnDefinitionDeployer = new DmnDefinitionDeployer(taktPropertiesHelper);
    this.processInstanceProducer =
        new ProcessInstanceProducer(
            taktPropertiesHelper, processInstanceTriggerEmitter, authorizationTokenProvider);
    this.messageEventSender = new MessageEventSender(taktPropertiesHelper);
    this.signalSender = new SignalSender(taktPropertiesHelper);
    this.processInstanceUpdateConsumer =
        new ProcessInstanceUpdateConsumer(taktPropertiesHelper, executor);
    this.processInstanceResponder = processInstanceResponder;
    this.externalTaskTriggerTopicConsumer =
        new ExternalTaskTriggerTopicConsumer(
            taktPropertiesHelper, executor, processInstanceResponder);
    this.userTaskTriggerTopicConsumer =
        new UserTaskTriggerTopicConsumer(taktPropertiesHelper, executor, processInstanceResponder);
  }

  /**
   * Creates a new TaktXClientBuilder instance to create a new TaktXClient.
   *
   * @return A new TaktXClientBuilder instance.
   */
  public static TaktXClientBuilder newClientBuilder() {
    return new TaktXClientBuilder();
  }

  /**
   * Starts the TaktXClient, which subscribes to process definition records and process definition
   * updates.
   */
  public void start() {
    initRuntimeConfigurationStore();
    refreshWorkerSigningFunctionRegistration();
    initSigningKeysStore();
    this.processDefinitionConsumer.subscribeToDefinitionRecords();
    this.xmlByProcessDefinitionIdConsumer.subscribeToTopic();
    publishWorkerSigningKeyIfConfigured();
  }

  private void initRuntimeConfigurationStore() {
    String bootstrapServers = taktPropertiesHelper.getBootstrapServers();
    if (bootstrapServers == null || bootstrapServers.isBlank()) {
      log.debug(
          "No bootstrap.servers configured — skipping RuntimeConfigurationStore initialisation");
      return;
    }
    String topic =
        taktPropertiesHelper.getPrefixedTopicName(
            io.taktx.Topics.CONFIGURATION_TOPIC.getTopicName());
    try {
      Properties consumerProps =
          taktPropertiesHelper.getKafkaConsumerProperties(
              "runtime-configuration-store-" + ProcessHandle.current().pid(),
              org.apache.kafka.common.serialization.StringDeserializer.class,
              org.apache.kafka.common.serialization.ByteArrayDeserializer.class,
              "earliest");
      runtimeConfigurationStore =
          new RuntimeConfigurationStore(
              consumerProps, topic, this::refreshWorkerSigningFunctionRegistration);
      runtimeConfigurationStore.awaitReady(java.time.Duration.ofSeconds(10));
      log.info(
          "✅ RuntimeConfigurationStore ready — signingEnabled={} engineRequiresAuthorization={}",
          RuntimeConfigurationHolder.isSigningEnabled(),
          RuntimeConfigurationHolder.isEngineRequiresAuthorization());
    } catch (Exception e) {
      RuntimeConfigurationHolder.clear();
      log.warn(
          "RuntimeConfigurationStore initialisation failed — using default runtime config: {}",
          e.getMessage());
    }
  }

  private void initSigningKeysStore() {
    String bootstrapServers = taktPropertiesHelper.getBootstrapServers();
    if (bootstrapServers == null || bootstrapServers.isBlank()) {
      log.debug("No bootstrap.servers configured — skipping SigningKeysStore initialisation");
      return;
    }
    String topic =
        taktPropertiesHelper.getPrefixedTopicName(
            io.taktx.Topics.SIGNING_KEYS_TOPIC.getTopicName());
    try {
      // Use the Properties-based constructor so auth/TLS settings flow through automatically,
      // following the same pattern as ProcessDefinitionDeployer and MessageEventSender.
      java.util.Properties consumerProps =
          taktPropertiesHelper.getKafkaConsumerProperties(
              "signing-keys-store-" + ProcessHandle.current().pid(),
              org.apache.kafka.common.serialization.StringDeserializer.class,
              org.apache.kafka.common.serialization.ByteArrayDeserializer.class,
              "earliest");
      signingKeysStore = new SigningKeysStore(consumerProps, topic);
      signingKeysStore.awaitReady(java.time.Duration.ofSeconds(10));
      SigningKeysStoreHolder.set(signingKeysStore);
      log.info(
          "✅ SigningKeysStore ready — {} key(s) loaded from {}",
          signingKeysStore.snapshot().size(),
          topic);
    } catch (Exception e) {
      log.warn(
          "SigningKeysStore initialisation failed — signature verification will be skipped: {}",
          e.getMessage());
    }
  }

  /**
   * Publishes the active license to the {@code taktx-configuration} compacted topic.
   *
   * <p>All engine nodes consume this topic as a global KTable ({@code
   * Stores.GLOBAL_CONFIGURATION}). On receiving a record with key {@code "license"}, the engine
   * parses the License3j payload and updates its in-memory license state immediately — no restart
   * required.
   *
   * <p>Called by the ingester whenever Platform Service pushes a new or updated license. Publishing
   * the same license text twice is idempotent — compaction retains only the latest record per key.
   *
   * @param licenseText raw License3j-signed license file content (UTF-8 plain text)
   */
  public void publishLicense(String licenseText) {
    if (licenseText == null || licenseText.isBlank()) {
      throw new IllegalArgumentException("licenseText must not be null or blank");
    }
    String topic =
        taktPropertiesHelper.getPrefixedTopicName(
            io.taktx.Topics.CONFIGURATION_TOPIC.getTopicName());
    java.util.Properties producerProps = taktPropertiesHelper.getKafkaProducerProperties();
    // Fail fast so callers notice connectivity problems immediately.
    producerProps.put("max.block.ms", "10000");
    producerProps.put("delivery.timeout.ms", "10000");
    producerProps.put("request.timeout.ms", "8000");
    try (org.apache.kafka.clients.producer.KafkaProducer<String, byte[]> producer =
        new org.apache.kafka.clients.producer.KafkaProducer<>(
            producerProps,
            new org.apache.kafka.common.serialization.StringSerializer(),
            new org.apache.kafka.common.serialization.ByteArraySerializer())) {
      byte[] valueBytes = licenseText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      producer.send(
          new org.apache.kafka.clients.producer.ProducerRecord<>(topic, "license", valueBytes));
      producer.flush();
      log.info("✅ License published to configuration topic: topic={}", topic);
    } catch (Exception e) {
      log.error("Failed to publish license to {}: {}", topic, e.getMessage(), e);
      throw new IllegalStateException("Failed to publish license", e);
    }
  }

  /**
   * Publishes cluster-wide runtime configuration to the {@code taktx-configuration} compacted topic
   * under key {@code "config"}.
   */
  public void publishGlobalConfig(GlobalConfigurationDTO configuration) {
    publishGlobalConfig(taktPropertiesHelper.getTaktProperties(), configuration);
  }

  /**
   * Static convenience overload for publishing runtime configuration without a running client
   * instance.
   */
  public static void publishGlobalConfig(
      Properties properties, GlobalConfigurationDTO configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    String topic =
        new TaktPropertiesHelper(properties)
            .getPrefixedTopicName(io.taktx.Topics.CONFIGURATION_TOPIC.getTopicName());

    java.util.Properties producerProps =
        new TaktPropertiesHelper(properties).getKafkaProducerProperties();
    producerProps.put("max.block.ms", "10000");
    producerProps.put("delivery.timeout.ms", "10000");
    producerProps.put("request.timeout.ms", "8000");

    try (org.apache.kafka.clients.producer.KafkaProducer<String, byte[]> producer =
        new org.apache.kafka.clients.producer.KafkaProducer<>(
            producerProps,
            new org.apache.kafka.common.serialization.StringSerializer(),
            new org.apache.kafka.common.serialization.ByteArraySerializer())) {
      byte[] valueBytes =
          CONFIG_OBJECT_MAPPER.writeValueAsBytes(buildConfigurationEvent(configuration));
      producer.send(
          new org.apache.kafka.clients.producer.ProducerRecord<>(
              topic, CONFIGURATION_RECORD_KEY, valueBytes));
      producer.flush();
      log.info("✅ Global configuration published to configuration topic: topic={}", topic);
    } catch (Exception e) {
      log.error("Failed to publish global configuration to {}: {}", topic, e.getMessage(), e);
      throw new IllegalStateException("Failed to publish global configuration", e);
    }
  }

  /**
   * Publishes an Ed25519 or RSA public key to the {@code taktx-signing-keys} compacted topic so
   * that all participants (engine, other workers, platform) can verify signatures produced by the
   * corresponding private key.
   *
   * <p>Use this method from:
   *
   * <ul>
   *   <li><b>Workers</b> — called automatically from {@link #start()} when {@code
   *       TAKTX_SIGNING_PUBLIC_KEY} is configured; call it explicitly if you manage key lifecycle
   *       yourself.
   *   <li><b>Platform / Ingester</b> — publish the RSA public key under the same {@code kid} value
   *       that will appear in issued JWT headers so the engine can verify inbound JWT commands.
   *   <li><b>Tests</b> — seed the signing-keys topic before starting the engine.
   * </ul>
   *
   * @param keyId unique identifier for this key (e.g. {@code "worker-billing-1"}, {@code
   *     "platform"})
   * @param publicKeyBase64 X.509 DER public key, base64-encoded
   * @param owner human-readable label, e.g. {@code "worker"}, {@code "platform"}, {@code "engine"}
   */
  public void publishSigningKey(String keyId, String publicKeyBase64, String owner) {
    publishSigningKey(keyId, publicKeyBase64, owner, "Ed25519");
  }

  /**
   * Publishes a public key with an explicit algorithm label such as {@code Ed25519} or {@code RSA}.
   * /** Publishes a public key with an explicit algorithm label such as {@code Ed25519} or {@code
   * RSA}.
   */
  public void publishSigningKey(
      String keyId, String publicKeyBase64, String owner, String algorithm) {
    publishSigningKey(keyId, publicKeyBase64, owner, algorithm, KeyRole.CLIENT);
  }

  /**
   * Publishes a public key with an explicit algorithm label and role. Use {@link KeyRole#CLIENT}
   * for worker/client keys (the default). Reserved overload for platform tooling that publishes
   * platform-level keys.
   */
  public void publishSigningKey(
      String keyId, String publicKeyBase64, String owner, String algorithm, KeyRole role) {
    publishSigningKey(keyId, publicKeyBase64, owner, algorithm, role, null);
  }

  /**
   * Publishes a public key with an explicit algorithm, role, and platform countersignature.
   *
   * <p>Use this overload in <em>anchored mode</em> ({@code TAKTX_PLATFORM_PUBLIC_KEY} is set on the
   * engine). The {@code registrationSignature} must be the base64-encoded RSA/SHA-256 signature
   * produced by the platform root private key over the key's canonical payload:
   *
   * <pre>{@code keyId|publicKeyBase64|algorithm|owner|role}</pre>
   *
   * <p>Generate with {@code scripts/generate_trust_anchor.sh --worker}. Without a valid
   * countersignature, the engine will reject all commands signed by this worker key when anchored
   * mode is active.
   *
   * @param registrationSignature base64-encoded RSA/SHA-256 countersignature, or {@code null} in
   *     community mode
   */
  public void publishSigningKey(
      String keyId,
      String publicKeyBase64,
      String owner,
      String algorithm,
      KeyRole role,
      @Nullable String registrationSignature) {
    new SigningKeyRegistrar(taktPropertiesHelper)
        .publishPublicKey(keyId, publicKeyBase64, owner, algorithm, role, registrationSignature);
    log.info(
        "✅ Signing key published: keyId={} owner={} algorithm={} role={} countersigned={}",
        keyId,
        owner,
        algorithm,
        role,
        registrationSignature != null);
  }

  /**
   * Static convenience overload for callers that do not yet have a running {@link TaktXClient}
   * instance — e.g. test setup code or platform bootstrap that runs before the client is started.
   *
   * <p>Builds a temporary {@link TaktPropertiesHelper} from {@code properties} so the topic name is
   * prefixed consistently with any running client using the same properties: {@code
   * [<tenantId>.]<namespace>.taktx-signing-keys}.
   *
   * @param properties must contain {@code bootstrap.servers}, {@code taktx.engine.tenant-id}, and
   *     {@code taktx.engine.namespace} — all three are required
   * @param keyId unique identifier for this key
   * @param publicKeyBase64 X.509 DER public key, base64-encoded
   * @param owner human-readable label, e.g. {@code "worker"}, {@code "platform"}
   */
  public static void publishSigningKey(
      Properties properties, String keyId, String publicKeyBase64, String owner) {
    publishSigningKey(properties, keyId, publicKeyBase64, owner, "Ed25519");
  }

  /**
   * Static convenience overload with an explicit algorithm label such as {@code Ed25519} or {@code
   * RSA}.
   */
  public static void publishSigningKey(
      Properties properties, String keyId, String publicKeyBase64, String owner, String algorithm) {
    publishSigningKey(properties, keyId, publicKeyBase64, owner, algorithm, KeyRole.CLIENT);
  }

  /** Static convenience overload with an explicit algorithm and role. */
  public static void publishSigningKey(
      Properties properties,
      String keyId,
      String publicKeyBase64,
      String owner,
      String algorithm,
      KeyRole role) {
    publishSigningKey(properties, keyId, publicKeyBase64, owner, algorithm, role, null);
  }

  /**
   * Static convenience overload with an explicit algorithm, role, and platform countersignature.
   *
   * <p>Use this overload in anchored mode. The {@code registrationSignature} must be the
   * base64-encoded RSA/SHA-256 signature produced by the platform root private key over {@code
   * keyId|publicKeyBase64|algorithm|owner|role}. Pass {@code null} in community mode.
   */
  public static void publishSigningKey(
      Properties properties,
      String keyId,
      String publicKeyBase64,
      String owner,
      String algorithm,
      KeyRole role,
      @Nullable String registrationSignature) {
    TaktPropertiesHelper helper = new TaktPropertiesHelper(properties);
    String topic = helper.getPrefixedTopicName(io.taktx.Topics.SIGNING_KEYS_TOPIC.getTopicName());
    SigningKeyRegistrar.publishPublicKey(
        helper.getBootstrapServers(),
        topic,
        keyId,
        publicKeyBase64,
        owner,
        algorithm,
        role,
        registrationSignature);
    log.info(
        "✅ Signing key published: keyId={} owner={} algorithm={} role={} countersigned={}",
        keyId,
        owner,
        algorithm,
        role,
        registrationSignature != null);
  }

  private void publishWorkerSigningKeyIfConfigured() {
    ensureWorkerKeyPublished(currentSigningIdentity());
  }

  static ConfigurationEventDTO buildConfigurationEvent(GlobalConfigurationDTO configuration) {
    return ConfigurationEventDTO.builder()
        .eventType(ConfigurationEventType.CONFIGURATION_UPDATE)
        .configuration(configuration)
        .timestamp(Instant.now())
        .build();
  }

  private SigningIdentity currentSigningIdentity() {
    return signingIdentitySource != null ? signingIdentitySource.currentIdentity() : null;
  }

  void refreshWorkerSigningFunctionRegistration() {
    boolean signingEnabled = RuntimeConfigurationHolder.isSigningEnabled();
    boolean authRequired = RuntimeConfigurationHolder.isEngineRequiresAuthorization();

    // ── Auth gate check ───────────────────────────────────────────────────
    if (authRequired && authorizationTokenProvider == null) {
      log.warn(
          "engineRequiresAuthorization=true in runtime config but no AuthorizationTokenProvider"
              + " is configured — entry commands (startProcess / abortElementInstance / setVariable) sent"
              + " without an explicit JWT token will be rejected by the engine."
              + " Configure taktx.oidc.* or supply an AuthorizationTokenProvider.");
    }

    // ── Signing gate check ────────────────────────────────────────────────
    if (!signingEnabled) {
      logWorkerSigningRegistrationState(
          "runtime-disabled",
          "Worker response signing inactive — runtime configuration signingEnabled=false");
      if (authRequired) {
        log.info(
            "engineRequiresAuthorization=true — entry commands require JWT"
                + " (X-TaktX-Authorization); non-entry commands are accepted without Ed25519");
      }
      return;
    }

    SigningIdentity identity = currentSigningIdentity();
    if (identity == null) {
      String sourceType =
          signingIdentitySource != null ? signingIdentitySource.getSourceType() : "none";
      logWorkerSigningRegistrationState(
          "waiting-for-identity:" + sourceType,
          "Worker response signing enabled by runtime configuration but no signing identity is"
              + " available yet from source={}",
          sourceType);
      return;
    }

    SigningServiceHolder.set(this::signWorkerPayload);
    if (authRequired) {
      // AND mode: entry commands need BOTH JWT (auth gate) AND Ed25519 (signing gate).
      // ENGINE-role keys satisfy both gates without JWT.
      logWorkerSigningRegistrationState(
          "active-and:" + identity.getKeyId(),
          "AND security mode active (engineRequiresAuthorization=true AND signingEnabled=true)"
              + " — entry commands require JWT + Ed25519; ENGINE-role keys satisfy both gates."
              + " source={} keyId={}",
          signingIdentitySource.getSourceType(),
          identity.getKeyId());
    } else {
      logWorkerSigningRegistrationState(
          "active:" + identity.getKeyId(),
          "Worker response signing active — runtime configuration enabled, source={} keyId={}",
          signingIdentitySource.getSourceType(),
          identity.getKeyId());
    }
  }

  private void logWorkerSigningRegistrationState(String newState, String message, Object... args) {
    if (newState.equals(workerSigningRegistrationState)) {
      return;
    }
    workerSigningRegistrationState = newState;
    log.info(message, args);
  }

  private boolean ensureWorkerKeyPublished(SigningIdentity identity) {
    if (identity == null) {
      return false;
    }
    if (!identity.hasPublicKey()) {
      log.debug(
          "No public key in signing identity — skipping worker key publication"
              + " (set TAKTX_SIGNING_PUBLIC_KEY or taktx.signing.public-key)");
      return true;
    }
    if (identity.getKeyId().equals(publishedWorkerKeyId)) {
      return true;
    }
    try {
      publishSigningKey(
          identity.getKeyId(),
          identity.getPublicKeyBase64(),
          resolveWorkerSigningOwner(identity),
          identity.getAlgorithm(),
          KeyRole.CLIENT,
          workerKeyRegistrationSignature);
      publishedWorkerKeyId = identity.getKeyId();
      return true;
    } catch (Exception e) {
      log.error("Failed to publish worker signing key: {}", e.getMessage(), e);
      return false;
    }
  }

  private String resolveWorkerSigningOwner(SigningIdentity identity) {
    if (identity == null) {
      return "worker";
    }
    String configuredOwner =
        TaktXClientBuilder.firstNonBlank(
            taktPropertiesHelper.getTaktProperties().getProperty("taktx.signing.owner"),
            System.getProperty("taktx.signing.owner"),
            System.getenv("TAKTX_SIGNING_OWNER"),
            taktPropertiesHelper.getTaktProperties().getProperty("quarkus.application.name"),
            taktPropertiesHelper.getTaktProperties().getProperty("spring.application.name"),
            taktPropertiesHelper.getTaktProperties().getProperty("application.name"));
    return configuredOwner != null && !configuredOwner.isBlank()
        ? configuredOwner
        : identity.getKeyId();
  }

  private String signWorkerPayload(byte[] payload) {
    if (!RuntimeConfigurationHolder.isSigningEnabled()) {
      return null;
    }
    SigningIdentity identity = currentSigningIdentity();
    if (identity == null) {
      return null;
    }
    if (!ensureWorkerKeyPublished(identity)) {
      return null;
    }
    try {
      byte[] sig = Ed25519Service.sign(payload, identity.getPrivateKeyBase64());
      return identity.toHeaderValue(sig);
    } catch (Exception e) {
      log.warn("Worker signing failed: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Stops only the external-task consumer threads, leaving the rest of the client running. Useful
   * in test harnesses where a single {@link TaktXClient} is reused across tests: calling this in
   * {@code @BeforeEach} ensures that stale topic subscriptions from a previous test (e.g. {@code
   * "service-task"}) do not bleed into the next test that never registers that task type. The
   * consumer is restarted lazily by the next {@link #registerExternalTaskConsumer} call.
   */
  public void stopExternalTaskConsumer() {
    this.externalTaskTriggerTopicConsumer.stop();
  }

  /** Stops the TaktXClient, which unsubscribes from process definition records and process */
  public void stop() {
    this.processDefinitionConsumer.stop();
    this.externalTaskTriggerTopicConsumer.stop();
    this.processInstanceUpdateConsumer.stop();
    this.xmlByProcessDefinitionIdConsumer.stop();
    if (signingKeysStore != null) {
      SigningKeysStoreHolder.clear();
      signingKeysStore.close();
      signingKeysStore = null;
    }
    if (runtimeConfigurationStore != null) {
      runtimeConfigurationStore.close();
      runtimeConfigurationStore = null;
    }
    publishedWorkerKeyId = null;
    workerSigningRegistrationState = "uninitialized";
    RuntimeConfigurationHolder.clear();
    SigningServiceHolder.clear();
  }

  /**
   * Requests creation of a Kafka topic for a worker with default settings (3 partitions, DELETE
   * cleanup policy, replication factor 1).
   *
   * <p>The 3-partition default keeps worker topics consistent with the managed fixed topics and
   * leaves room for throughput scaling within the deployment's partition budget. Use the full
   * overload to specify a different count — lower for budget-constrained deployments, higher for
   * high-throughput workers.
   *
   * @param externalTaskId the task type identifier (e.g. {@code "invoice-processor"})
   * @return the prefixed Kafka topic name that was requested
   */
  public String requestExternalTaskTopic(String externalTaskId) {
    return this.externalTaskTopicRequester.requestExternalTaskTopic(externalTaskId);
  }

  /**
   * Requests the creation of a Kafka topic for an external task with explicit settings.
   *
   * <p>The engine enforces a total partition budget across all managed topics. If the requested
   * partitions would push the total above the licensed budget, the request is rejected gracefully
   * (a warning is logged and no topic is created). The worker should handle a missing topic by
   * retrying or falling back to a lower partition count.
   *
   * @param externalTaskId the task type identifier (e.g. {@code "invoice-processor"})
   * @param partitions desired partition count — subject to the deployment's partition budget
   * @param cleanupPolicy the Kafka cleanup policy for the topic
   * @param replicationFactor the replication factor for the topic
   * @return the prefixed Kafka topic name that was requested
   */
  public String requestExternalTaskTopic(
      String externalTaskId, int partitions, CleanupPolicy cleanupPolicy, short replicationFactor) {
    return this.externalTaskTopicRequester.requestExternalTaskTopic(
        externalTaskId, partitions, cleanupPolicy, replicationFactor);
  }

  /**
   * Deploys a process definition from an InputStream.
   *
   * @param inputStream The InputStream containing the process definition XML.
   * @return The parsed definitions DTO.
   * @throws IOException If an error occurs while reading the InputStream.
   */
  public ParsedDefinitionsDTO deployProcessDefinition(InputStream inputStream) throws IOException {
    return this.processDefinitionDeployer.deployInputStream(new String(inputStream.readAllBytes()));
  }

  /**
   * Deploys a DMN definition from an InputStream.
   *
   * @param inputStream The InputStream containing the DMN XML.
   * @return The parsed DMN definitions DTO.
   * @throws IOException If an error occurs while reading the InputStream.
   */
  public io.taktx.dto.ParsedDmnDefinitionsDTO deployDmnDefinition(InputStream inputStream)
      throws IOException {
    return this.dmnDefinitionDeployer.deployInputStream(new String(inputStream.readAllBytes()));
  }

  /**
   * Retrieves a deployed process definition by its ID and hash.
   *
   * @param processDefinitionId The ID of the process definition.
   * @param hash The hash of the process definition.
   * @return An Optional containing the ProcessDefinitionDTO if found, or empty if not found.
   */
  public Optional<ProcessDefinitionDTO> getProcessDefinitionByHash(
      String processDefinitionId, String hash) {
    return this.processDefinitionConsumer.getDeployedProcessDefinitionbyHash(
        processDefinitionId, hash);
  }

  /**
   * Starts a new process instance of the latest version of the given process definition.
   *
   * @param process The ID of the process definition to start.
   * @param variables The initial variables for the process instance.
   * @return The UUID of the started process instance.
   */
  public UUID startProcess(String process, VariablesDTO variables) {
    return processInstanceProducer.startProcess(process, variables);
  }

  public UUID startProcess(String process, int version, VariablesDTO variables) {
    return processInstanceProducer.startProcess(process, version, variables);
  }

  /**
   * Starts a new process instance with a Platform Service authorization token.
   *
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public UUID startProcess(
      String process, int version, VariablesDTO variables, @Nullable String authorizationToken) {
    return processInstanceProducer.startProcess(process, version, variables, authorizationToken);
  }

  /**
   * Sends a message event to the engine.
   *
   * @param messageEventDTO The message event DTO containing the message details.
   */
  public void sendMessage(MessageEventDTO messageEventDTO) {
    messageEventSender.sendMessage(messageEventDTO);
  }

  /**
   * Registers a consumer that will be notified of instance update records, resuming from the last
   * committed offset for this consumer group.
   *
   * @param groupId The Kafka consumer group ID to use.
   * @param consumer The consumer to register.
   */
  public void registerInstanceUpdateConsumer(
      String groupId, Consumer<List<InstanceUpdateRecord>> consumer) {
    this.processInstanceUpdateConsumer.registerInstanceUpdateConsumer(groupId, consumer);
  }

  /**
   * Registers a consumer that will be notified of instance update records, with explicit control
   * over where reading begins.
   *
   * <p>{@link InstanceUpdateStartStrategy#RESUME} (the default via the 2-arg overload) resumes from
   * the last committed offset. {@link InstanceUpdateStartStrategy#EARLIEST} seeks to offset 0 on
   * every assigned partition after the initial rebalance, guaranteeing a full-history replay
   * regardless of any previously committed offsets for this consumer group. This is the only
   * reliable way to replay from the beginning — {@code auto.offset.reset=earliest} is ignored by
   * Kafka once a group has committed offsets.
   *
   * @param groupId The Kafka consumer group ID to use.
   * @param consumer The consumer to register.
   * @param strategy {@link InstanceUpdateStartStrategy#RESUME} to continue from committed offsets;
   *     {@link InstanceUpdateStartStrategy#EARLIEST} to seek to the beginning of each partition
   *     after assignment.
   */
  public void registerInstanceUpdateConsumer(
      String groupId,
      Consumer<List<InstanceUpdateRecord>> consumer,
      InstanceUpdateStartStrategy strategy) {
    this.processInstanceUpdateConsumer.registerInstanceUpdateConsumer(groupId, consumer, strategy);
  }

  /**
   * Registers a consumer that will be notified of process definition updates.
   *
   * @param consumer The consumer to register.
   */
  public void registerProcessDefinitionUpdateConsumer(
      BiConsumer<ProcessDefinitionKey, ProcessDefinitionDTO> consumer) {
    this.processDefinitionConsumer.registerProcessDefinitionUpdateConsumer(consumer);
  }

  /** Deploys all classes annotated with @TaktDeployment found in the classpath. */
  public void deployTaktDeploymentAnnotatedClasses() {
    Set<Deployment> deployments = AnnotationScanner.findTaktDeployments();
    for (Deployment annotation : deployments) {
      String[] resources = annotation.resources();

      String joined = String.join(",", resources);
      log.info("Deploying process definition from resource {}", joined);

      for (String resource : resources) {
        // Get the input stream for each resource, support classpath, filesystem and wildcards
        processDefinitionDeployer.deployResource(resource);
      }
    }
  }

  /**
   * Responds to an external task trigger.
   *
   * @param externalTaskTriggerDTO The external task trigger DTO.
   * @return The ExternalTaskInstanceResponder to respond to the external task.
   */
  public ExternalTaskInstanceResponder respondToExternalTask(
      ExternalTaskTriggerDTO externalTaskTriggerDTO) {
    return processInstanceResponder.responderForExternalTaskTrigger(externalTaskTriggerDTO);
  }

  /**
   * Responds to an external task trigger.
   *
   * @param processInstanceId process instance id
   * @param elementInstanceIdPath the path to the element instance id
   * @return The ExternalTaskInstanceResponder to respond to the external task.
   */
  public ExternalTaskInstanceResponder respondToExternalTask(
      UUID processInstanceId, List<Long> elementInstanceIdPath) {
    return processInstanceResponder.responderForExternalTask(
        processInstanceId, elementInstanceIdPath);
  }

  /**
   * Completes a user task.
   *
   * @param userTaskTriggerDTO The user task trigger DTO.
   * @return The UserTaskInstanceResponder to respond to the user task.
   */
  public UserTaskInstanceResponder completeUserTask(UserTaskTriggerDTO userTaskTriggerDTO) {
    return processInstanceResponder.responderForUserTaskTrigger(userTaskTriggerDTO);
  }

  /**
   * Set variables in a scope.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the scope.
   * @param variables The variables to set.
   */
  public void setVariable(
      UUID processInstanceId, List<Long> elementInstanceIdPath, VariablesDTO variables) {
    processInstanceProducer.setVariable(processInstanceId, elementInstanceIdPath, variables);
  }

  /**
   * Set variables in a scope, attaching a Platform Service authorization token.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the scope.
   * @param variables The variables to set.
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null} for
   *     unauthenticated deployments
   */
  public void setVariable(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    processInstanceProducer.setVariable(
        processInstanceId, elementInstanceIdPath, variables, authorizationToken);
  }

  /**
   * Terminates a process instance.
   *
   * @param processInstanceId The UUID of the process instance to terminate.
   */
  public void abortElementInstance(UUID processInstanceId) {
    processInstanceProducer.abortProcessInstance(processInstanceId);
  }

  /**
   * Aborts a specific element instance within a process instance.
   *
   * @param activeProcessInstanceId The UUID of the active process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the element to abort.
   */
  public void abortElementInstance(UUID activeProcessInstanceId, List<Long> elementInstanceIdPath) {
    processInstanceProducer.abortElementInstance(activeProcessInstanceId, elementInstanceIdPath);
  }

  /**
   * Aborts an element instance with a Platform Service authorization token.
   *
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void abortElementInstance(
      UUID activeProcessInstanceId,
      List<Long> elementInstanceIdPath,
      @Nullable String authorizationToken) {
    processInstanceProducer.abortElementInstance(
        activeProcessInstanceId, elementInstanceIdPath, authorizationToken);
  }

  /**
   * Registers an external task consumer that will be notified of external task triggers.
   *
   * @param externalTaskTriggerConsumer The external task trigger consumer to register.
   * @param groupId The group ID for the consumer.
   */
  public void registerExternalTaskConsumer(
      ExternalTaskTriggerConsumer externalTaskTriggerConsumer, String groupId) {
    this.externalTaskTriggerTopicConsumer.subscribeToExternalTaskTriggerTopics(
        externalTaskTriggerConsumer, groupId);
  }

  /**
   * Registers a user task consumer that will be notified of user task triggers.
   *
   * @param userTaskTriggerConsumer The user task trigger consumer to register.
   */
  public void registerUserTaskConsumer(UserTaskTriggerConsumer userTaskTriggerConsumer) {
    this.userTaskTriggerTopicConsumer.subscribeToUserTaskTriggerTopics(userTaskTriggerConsumer);
  }

  /**
   * Retrieves the XML of a process definition by its key.
   *
   * @param processDefinitionKey The key of the process definition.
   * @return The XML of the process definition.
   * @throws IOException If an error occurs while retrieving the XML.
   */
  public String getProcessDefinitionXml(ProcessDefinitionKey processDefinitionKey)
      throws IOException {
    return this.xmlByProcessDefinitionIdConsumer.getProcessDefinitionXml(processDefinitionKey);
  }

  /**
   * Sends a signal event to the engine.
   *
   * @param signalName The name of the signal to send.
   */
  public void sendSignal(String signalName) {
    this.signalSender.sendMSignal(new SignalDTO(signalName));
  }

  /**
   * Gets the ProcessDefinitionConsumer instance.
   *
   * @return The ProcessDefinitionConsumer.
   */
  public ProcessDefinitionConsumer getProcessDefinitionConsumer() {
    return this.processDefinitionConsumer;
  }

  /**
   * Gets the TaktParameterResolverFactory instance.
   *
   * @return The TaktParameterResolverFactory.
   */
  public ParameterResolverFactory getParameterResolverFactory() {
    return this.parameterResolverFactory;
  }

  /**
   * Gets the ResultProcessorFactory instance.
   *
   * @return The ResultProcessorFactory.
   */
  public ResultProcessorFactory getResultProcessorFactory() {
    return resultProcessorFactory;
  }

  /**
   * Gets the ProcessInstanceResponder instance.
   *
   * @return The ProcessInstanceResponder.
   */
  public ProcessInstanceResponder getProcessInstanceResponder() {
    return this.processInstanceResponder;
  }

  /**
   * Gets the ExternalTaskTopicRequester instance.
   *
   * @return The ExternalTaskTopicRequester.
   */
  public ExternalTaskTopicRequester getExternalTaskTopicRequester() {
    return this.externalTaskTopicRequester;
  }

  /**
   * Builder class for creating TaktXClient instances. Requires NAMESPACE, and
   * KAFKA_BOOTSTRAP_SERVERS environment variables to be set or configured via the builder methods.
   */
  public static class TaktXClientBuilder {

    private Properties properties;
    private ParameterResolverFactory parameterResolverFactory;
    private ResultProcessorFactory resultProcessorFactory;
    private SigningIdentitySource signingIdentitySource;
    private AuthorizationTokenProvider authorizationTokenProvider;
    private String workerKeyRegistrationSignature;

    private TaktXClientBuilder() {}

    /**
     * Builds and returns a TaktXClient instance.
     *
     * @return A TaktXClient instance.
     * @throws IllegalArgumentException if Kafka properties are not set.
     */
    public TaktXClient build() {
      if (properties == null) {
        throw new IllegalArgumentException("TaktX properties should be passed");
      }

      TaktPropertiesHelper taktPropertiesHelper = new TaktPropertiesHelper(properties);

      SigningIdentitySource effectiveSigningIdentitySource =
          resolveSigningIdentitySource(properties);
      AuthorizationTokenProvider effectiveAuthorizationTokenProvider =
          resolveAuthorizationTokenProvider(properties);
      String effectiveRegistrationSignature = resolveWorkerKeyRegistrationSignature(properties);

      // Wrap the value serializer with SigningSerializer so signing happens in one pass
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter =
          new KafkaProducer<>(
              taktPropertiesHelper.getKafkaProducerProperties(),
              new io.taktx.util.TaktUUIDSerializer(),
              new SigningSerializer<>(new ProcessInstanceTriggerSerializer()));

      ProcessInstanceResponder externalTaskResponder =
          new ProcessInstanceResponder(taktPropertiesHelper, processInstanceTriggerEmitter);

      ParameterResolverFactory clientParameterResolverFactory =
          this.parameterResolverFactory != null
              ? this.parameterResolverFactory
              : new DefaultParameterResolverFactory(externalTaskResponder);
      ResultProcessorFactory clientResultProcessorFactory =
          this.resultProcessorFactory != null
              ? this.resultProcessorFactory
              : new DefaultResultProcessorFactory();
      TaktXClient client =
          new TaktXClient(
              taktPropertiesHelper,
              processInstanceTriggerEmitter,
              externalTaskResponder,
              clientParameterResolverFactory,
              clientResultProcessorFactory,
              effectiveSigningIdentitySource,
              effectiveAuthorizationTokenProvider,
              effectiveRegistrationSignature);
      externalTaskResponder.setBeforeSendHook(client::refreshWorkerSigningFunctionRegistration);
      SigningIdentity identity = client.currentSigningIdentity();
      if (identity != null) {
        log.info(
            "Worker response signing configured from source={} (keyId={})",
            effectiveSigningIdentitySource.getSourceType(),
            identity.getKeyId());
      }
      if (effectiveAuthorizationTokenProvider != null) {
        log.info(
            "Client command authorization configured via provider={} for start/abort commands",
            effectiveAuthorizationTokenProvider.getClass().getSimpleName());
      }
      return client;
    }

    AuthorizationTokenProvider resolveAuthorizationTokenProvider(Properties properties) {
      if (authorizationTokenProvider != null) {
        return authorizationTokenProvider;
      }
      if (!OpenIdClientCredentialsTokenProvider.hasConfiguration(properties)) {
        return null;
      }
      return OpenIdClientCredentialsTokenProvider.fromProperties(properties);
    }

    SigningIdentitySource resolveSigningIdentitySource(Properties properties) {
      if (signingIdentitySource != null) {
        return signingIdentitySource;
      }

      String sourceType =
          firstNonBlank(
              properties.getProperty("taktx.signing.identity-source"),
              System.getProperty("taktx.signing.identity-source"),
              System.getenv("TAKTX_SIGNING_IDENTITY_SOURCE"));
      String keyIdOverride = properties.getProperty("taktx.signing.key-id");

      if (sourceType == null || sourceType.isBlank()) {
        EnvironmentWorkerSigningIdentitySource environmentSource =
            new EnvironmentWorkerSigningIdentitySource(properties, keyIdOverride);
        SigningIdentity identity = environmentSource.currentIdentity();
        if (identity != null) {
          return environmentSource;
        }
        log.info(
            "No worker signing identity configured via environment/system properties — falling back to generated keypair");
        return new GeneratedSigningIdentitySource("client-");
      }
      if ("env".equalsIgnoreCase(sourceType) || "environment".equalsIgnoreCase(sourceType)) {
        return new EnvironmentWorkerSigningIdentitySource(properties, keyIdOverride);
      }
      if ("file".equalsIgnoreCase(sourceType)) {
        return new FileSigningIdentitySource(properties);
      }
      if ("generated".equalsIgnoreCase(sourceType)) {
        return new GeneratedSigningIdentitySource("client-");
      }
      throw new IllegalArgumentException(
          "Unsupported taktx.signing.identity-source='"
              + sourceType
              + "'. Supported values: env, file, generated");
    }

    private static String firstNonBlank(String... candidates) {
      for (String candidate : candidates) {
        if (candidate != null && !candidate.isBlank()) {
          return candidate;
        }
      }
      return null;
    }

    /**
     * Sets the TaktParameterResolverFactory to be used by the TaktXClient.
     *
     * @param parameterResolverFactory The TaktParameterResolverFactory instance.
     * @return The TaktXClientBuilder instance.
     */
    public TaktXClientBuilder withTaktParameterResolverFactory(
        ParameterResolverFactory parameterResolverFactory) {
      this.parameterResolverFactory = parameterResolverFactory;
      return this;
    }

    public TaktXClientBuilder withResultProcessorFactory(
        ResultProcessorFactory resultProcessorFactory) {
      this.resultProcessorFactory = resultProcessorFactory;
      return this;
    }

    /**
     * Sets the TaktX properties to be used by the TaktXClient.
     *
     * @param properties The TaktX properties.
     * @return The TaktXClientBuilder instance.
     */
    public TaktXClientBuilder withProperties(Properties properties) {
      this.properties = properties;
      return this;
    }

    public TaktXClientBuilder withSigningIdentitySource(
        SigningIdentitySource signingIdentitySource) {
      this.signingIdentitySource = signingIdentitySource;
      return this;
    }

    /**
     * Sets the platform countersignature for this worker's signing key.
     *
     * <p>Required when the engine operates in <em>anchored mode</em> ({@code
     * TAKTX_PLATFORM_PUBLIC_KEY} is configured). The value is the base64-encoded RSA/SHA-256
     * signature produced by the platform root private key over:
     *
     * <pre>{@code keyId|publicKeyBase64|Ed25519|owner|CLIENT}</pre>
     *
     * <p>Generate with {@code scripts/generate_trust_anchor.sh --worker}. Alternatively, set the
     * {@code taktx.signing.registration-signature} property or the {@code
     * TAKTX_SIGNING_REGISTRATION_SIGNATURE} environment variable — the builder reads these
     * automatically if this method is not called.
     *
     * @param registrationSignature base64-encoded RSA/SHA-256 countersignature, or {@code null} in
     *     community mode
     */
    public TaktXClientBuilder withSigningRegistrationSignature(String registrationSignature) {
      this.workerKeyRegistrationSignature = registrationSignature;
      return this;
    }

    String resolveWorkerKeyRegistrationSignature(Properties properties) {
      if (workerKeyRegistrationSignature != null) {
        return workerKeyRegistrationSignature;
      }
      return firstNonBlank(
          properties.getProperty("taktx.signing.registration-signature"),
          System.getProperty("taktx.signing.registration-signature"),
          System.getenv("TAKTX_SIGNING_REGISTRATION_SIGNATURE"));
    }

    public TaktXClientBuilder withAuthorizationTokenProvider(
        AuthorizationTokenProvider authorizationTokenProvider) {
      this.authorizationTokenProvider = authorizationTokenProvider;
      return this;
    }
  }
}
