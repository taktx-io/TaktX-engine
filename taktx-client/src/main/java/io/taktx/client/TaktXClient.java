/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.CleanupPolicy;
import io.taktx.client.annotation.Deployment;
import io.taktx.client.auth.AuthorizationTokenProvider;
import io.taktx.client.auth.OpenIdClientCredentialsTokenProvider;
import io.taktx.client.dlq.DlqEntryConsumer;
import io.taktx.client.dlq.DlqReplayCommandProducer;
import io.taktx.client.dlq.DlqReplayResultConsumer;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.ConfigurationEventDTO.ConfigurationEventType;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.DlqReplayResult;
import io.taktx.dto.DmnDefinitionKey;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventSeverity;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityParticipantDescriptor;
import io.taktx.dto.SecurityPostureIssueCodes;
import io.taktx.dto.SignalDTO;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EnvironmentWorkerSigningIdentitySource;
import io.taktx.security.FileSigningIdentitySource;
import io.taktx.security.GeneratedSigningIdentitySource;
import io.taktx.security.LocalPersistentSigningIdentitySource;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SecurityParticipantDescriptorSupport;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningIdentitySource;
import io.taktx.security.SigningKeyRegistrar;
import io.taktx.security.SigningKeysStore;
import io.taktx.security.SigningKeysStoreHolder;
import io.taktx.security.SigningServiceHolder;
import io.taktx.serdes.ConfigurationProtoMapper;
import io.taktx.serdes.NamespaceSecurityPolicyProtoMapper;
import io.taktx.serdes.ProcessInstanceTriggerProtoMapper;
import io.taktx.serdes.ProtoSigningSerializer;
import io.taktx.topicmanagement.ExternalTaskTopicRequester;
import io.taktx.util.TaktPropertiesHelper;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
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

  private static final int DEFAULT_SECURITY_EVENT_HISTORY_SIZE = 256;

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(TaktXClient.class);
  private final ProcessDefinitionConsumer processDefinitionConsumer;
  static final String CONFIGURATION_RECORD_KEY = "config";
  static final String SECURITY_POLICY_RECORD_KEY = "policy";
  private final ParameterResolverFactory parameterResolverFactory;
  private final ProcessInstanceResponder processInstanceResponder;
  private final ProcessDefinitionDeployer processDefinitionDeployer;
  private final DmnDefinitionDeployer dmnDefinitionDeployer;
  private final ProcessInstanceProducer processInstanceProducer;
  private final ProcessInstanceUpdateConsumer processInstanceUpdateConsumer;
  private final XmlByProcessDefinitionIdConsumer xmlByProcessDefinitionIdConsumer;
  private final XmlByDmnDefinitionIdConsumer xmlByDmnDefinitionIdConsumer;
  private final MessageEventSender messageEventSender;
  private final SignalSender signalSender;
  private final ExternalTaskTriggerTopicConsumer externalTaskTriggerTopicConsumer;
  private final UserTaskTriggerTopicConsumer userTaskTriggerTopicConsumer;
  private final ExternalTaskTopicRequester externalTaskTopicRequester;
  private final ResultProcessorFactory resultProcessorFactory;
  private final TaktPropertiesHelper taktPropertiesHelper;
  private final boolean anchored;
  private final SigningIdentitySource signingIdentitySource;
  private final @Nullable AuthorizationTokenProvider authorizationTokenProvider;
  private final SecurityParticipantDescriptor participantDescriptor;
  private final ClientProtectedDataPlaneParticipationGuard protectedDataPlaneParticipationGuard;
  private final ClientParticipantStatusPublisher participantStatusPublisher;

  // ── DLQ client (lazily initialised on first use) ──────────────────────────────
  private DlqEntryConsumer dlqEntryConsumer;
  private DlqReplayCommandProducer dlqReplayCommandProducer;
  private DlqReplayResultConsumer dlqReplayResultConsumer;

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
  private ClientParticipantStatusStore participantStatusStore;
  private ParticipantStatusTopicStore participantStatusTopicStore;
  private ClientSecurityEventStore securityEventStore;
  private SecurityEventTopicStore securityEventTopicStore;
  private RuntimeClient runtimeClient;
  private WorkersClient workersClient;
  private DlqClient dlqClient;
  private SecurityObservabilityClient securityObservabilityClient;
  private final CopyOnWriteArrayList<NamespaceSecurityPolicyConsumer>
      namespaceSecurityPolicyConsumers = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<ParticipantStatusConsumer> participantStatusConsumers =
      new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<SecurityEventConsumer> securityEventConsumers =
      new CopyOnWriteArrayList<>();
  private volatile String publishedWorkerKeyId;
  private volatile String publishedWorkerIdentityDescriptor;
  private volatile SigningKeyDTO publishedWorkerKeyRecord;
  private volatile String activeWorkerIdentityDescriptor;
  private volatile String workerSigningRegistrationState = "uninitialized";
  private volatile boolean globalWorkerSigningFunctionRegistered;
  private final SigningServiceHolder.SigningFunction globalWorkerSigningFunction;

  private TaktXClient(
      TaktPropertiesHelper taktPropertiesHelper,
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter,
      ProcessInstanceResponder processInstanceResponder,
      ParameterResolverFactory parameterResolverFactory,
      ResultProcessorFactory resultProcessorFactory,
      SecurityParticipantDescriptor participantDescriptor,
      SigningIdentitySource signingIdentitySource,
      @Nullable AuthorizationTokenProvider authorizationTokenProvider,
      @Nullable String workerKeyRegistrationSignature) {
    Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    this.taktPropertiesHelper = taktPropertiesHelper;
    this.anchored = resolvePlatformPublicKey(taktPropertiesHelper.getTaktProperties()) != null;
    this.participantDescriptor = participantDescriptor;
    this.signingIdentitySource = signingIdentitySource;
    this.authorizationTokenProvider = authorizationTokenProvider;
    this.workerKeyRegistrationSignature = workerKeyRegistrationSignature;
    this.globalWorkerSigningFunction = this::signWorkerPayload;
    this.externalTaskTopicRequester = new ExternalTaskTopicRequester(taktPropertiesHelper);
    this.parameterResolverFactory = parameterResolverFactory;
    this.resultProcessorFactory = resultProcessorFactory;
    this.processDefinitionConsumer = new ProcessDefinitionConsumer(taktPropertiesHelper, executor);
    this.xmlByProcessDefinitionIdConsumer =
        new XmlByProcessDefinitionIdConsumer(taktPropertiesHelper, executor);
    this.xmlByDmnDefinitionIdConsumer =
        new XmlByDmnDefinitionIdConsumer(taktPropertiesHelper, executor);
    this.processDefinitionDeployer = new ProcessDefinitionDeployer(taktPropertiesHelper);
    this.dmnDefinitionDeployer = new DmnDefinitionDeployer(taktPropertiesHelper);
    this.processInstanceProducer =
        new ProcessInstanceProducer(
            taktPropertiesHelper, processInstanceTriggerEmitter, authorizationTokenProvider);
    this.messageEventSender =
        new MessageEventSender(taktPropertiesHelper, () -> globalWorkerSigningFunction);
    this.signalSender = new SignalSender(taktPropertiesHelper, () -> globalWorkerSigningFunction);
    this.processInstanceUpdateConsumer =
        new ProcessInstanceUpdateConsumer(taktPropertiesHelper, executor);
    this.processInstanceResponder = processInstanceResponder;
    this.externalTaskTriggerTopicConsumer =
        new ExternalTaskTriggerTopicConsumer(
            taktPropertiesHelper, executor, processInstanceResponder);
    this.userTaskTriggerTopicConsumer =
        new UserTaskTriggerTopicConsumer(taktPropertiesHelper, executor, processInstanceResponder);
    this.protectedDataPlaneParticipationGuard =
        new ClientProtectedDataPlaneParticipationGuard(
            anchored,
            participantDescriptor,
            this::hasPublishedSigningCapability,
            Clock.systemUTC());
    this.participantStatusPublisher =
        new ClientParticipantStatusPublisher(
            taktPropertiesHelper,
            participantDescriptor,
            anchored,
            this::clientSigningConfigured,
            this::clientSigningKeyPublished,
            this::clientSigningKeyCountersigned,
            this::currentSigningKeyIdString,
            Clock.systemUTC());
    ProtectedClientDataPlaneGuard guard = this::ensureProtectedDataPlaneOperationAllowed;
    this.processInstanceProducer.setProtectedDataPlaneGuard(guard);
    this.messageEventSender.setProtectedDataPlaneGuard(guard);
    this.signalSender.setProtectedDataPlaneGuard(guard);
    this.processInstanceResponder.setProtectedDataPlaneGuard(guard);
    this.externalTaskTriggerTopicConsumer.setBeforeDispatchHook(
        () ->
            ensureProtectedDataPlaneOperationAllowed(
                ProtectedClientDataPlaneOperation.EXTERNAL_TASK_CONSUME, null));
    this.userTaskTriggerTopicConsumer.setBeforeDispatchHook(
        () ->
            ensureProtectedDataPlaneOperationAllowed(
                ProtectedClientDataPlaneOperation.USER_TASK_CONSUME, null));
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
   * Builds the default explicit participant descriptor for a client application using the supplied
   * properties and any embedded application-name hints.
   */
  public static SecurityParticipantDescriptor defaultClientParticipantDescriptor(
      Properties properties) {
    return defaultClientParticipantDescriptor(properties, null);
  }

  /**
   * Builds the default explicit participant descriptor for a client application using the supplied
   * properties and preferred component label.
   */
  public static SecurityParticipantDescriptor defaultClientParticipantDescriptor(
      Properties properties, @Nullable String preferredComponentType) {
    if (properties == null) {
      throw new IllegalArgumentException("properties must not be null");
    }
    TaktPropertiesHelper helper = new TaktPropertiesHelper(properties);

    // Resolve signing key ID: property > system property > env var
    String signingKeyId =
        TaktXClientBuilder.firstNonBlank(
            properties.getProperty("taktx.signing.key-id"),
            System.getProperty("taktx.signing.key-id"),
            System.getenv("TAKTX_SIGNING_KEY_ID"));

    // When a signing key ID is configured, derive componentType from its first dash-segment.
    // Otherwise fall back to the application name / explicit override.
    String componentType;
    if (signingKeyId != null
        && !signingKeyId.isBlank()
        && properties.getProperty("taktx.client.component-type") == null
        && preferredComponentType == null) {
      componentType = signingKeyId.split("-", 2)[0];
    } else {
      componentType =
          TaktXClientBuilder.firstNonBlank(
              properties.getProperty("taktx.client.component-type"),
              preferredComponentType,
              properties.getProperty("quarkus.application.name"),
              properties.getProperty("spring.application.name"),
              properties.getProperty("application.name"),
              "generic-client");
    }
    String normalizedComponentType =
        componentType != null ? componentType.trim() : "generic-client";
    Set<ParticipantCapability> capabilities = new LinkedHashSet<>();
    capabilities.add(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT);
    capabilities.add(ParticipantCapability.SECURITY_OBSERVER);

    TaktXClientBuilder builder = new TaktXClientBuilder();

    // Participant ID priority:
    // 1. taktx.client.participant-id  (explicit override)
    // 2. taktx.participant.id          (explicit override)
    // 3. taktx.signing.key-id          (when signing is configured — IS the participant ID)
    // 4. tenantId.namespace.componentType  (unsigned / fallback)
    String participantId =
        TaktXClientBuilder.firstNonBlank(
            properties.getProperty("taktx.client.participant-id"),
            properties.getProperty("taktx.participant.id"),
            signingKeyId,
            helper.getNamespace() + "." + normalizeParticipantIdSegment(normalizedComponentType));

    SecurityParticipantDescriptor descriptor =
        SecurityParticipantDescriptorSupport.requireValid(
            new SecurityParticipantDescriptor(
                participantId, ParticipantKind.CLIENT, capabilities, normalizedComponentType));
    return builder.validateClientParticipantDescriptor(properties, descriptor);
  }

  private static String normalizeParticipantIdSegment(String value) {
    if (value == null || value.isBlank()) {
      return "client";
    }
    String normalized =
        value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    normalized = normalized.replaceAll("-+", "-");
    normalized = normalized.replaceAll("^[._-]+", "").replaceAll("[._-]+$", "");
    return normalized.isBlank() ? "client" : normalized;
  }

  /**
   * Starts the TaktXClient, which subscribes to process definition records and process definition
   * updates.
   */
  public void start() {
    initRuntimeConfigurationStore();
    ensureObservabilityStoresInitialized();
    if (anchored && declaresCapability(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)) {
      // Register signing function once at startup — no policy-driven re-registration.
      refreshWorkerSigningFunctionRegistration();
    }
    initSigningKeysStore();
    this.processDefinitionConsumer.subscribeToDefinitionRecords();
    this.xmlByProcessDefinitionIdConsumer.subscribeToTopic();
    this.xmlByDmnDefinitionIdConsumer.subscribeToTopic();
    if (declaresCapability(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)) {
      publishWorkerSigningKeyIfConfigured();
      startParticipantStatusPublisher();
    }
  }

  private void startParticipantStatusPublisher() {
    String bootstrapServers = taktPropertiesHelper.getBootstrapServers();
    if (bootstrapServers == null || bootstrapServers.isBlank()) {
      log.debug(
          "No bootstrap.servers configured — skipping ClientParticipantStatusPublisher start");
      return;
    }
    try {
      participantStatusPublisher.start();
    } catch (Exception e) {
      log.warn("Failed to start client participant status publisher: {}", e.getMessage());
    }
  }

  private synchronized void ensureObservabilityStoresInitialized() {
    initParticipantStatusStore();
    initSecurityEventStore();
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
      runtimeConfigurationStore = new RuntimeConfigurationStore(consumerProps, topic, () -> {});
      runtimeConfigurationStore.awaitReady(java.time.Duration.ofSeconds(10));
      log.info(
          "✅ RuntimeConfigurationStore ready — signingEnabled={} engineRequiresAuthorization={} engineRequiresExternalTaskAuthorization={} engineRequiresUserTaskAuthorization={} replayProtectionMode={} replayProtectionRetentionMs={}",
          RuntimeConfigurationHolder.isSigningEnabled(),
          RuntimeConfigurationHolder.isEngineRequiresAuthorization(),
          RuntimeConfigurationHolder.isEngineRequiresExternalTaskAuthorization(),
          RuntimeConfigurationHolder.isEngineRequiresUserTaskAuthorization(),
          RuntimeConfigurationHolder.getReplayProtectionMode(),
          RuntimeConfigurationHolder.getReplayProtectionRetentionMs());
    } catch (Exception e) {
      RuntimeConfigurationHolder.clear();
      log.warn(
          "RuntimeConfigurationStore initialisation failed — using default runtime config: {}",
          e.getMessage());
    }
  }

  private void initParticipantStatusStore() {
    if (participantStatusStore != null && participantStatusTopicStore != null) {
      return;
    }
    String bootstrapServers = taktPropertiesHelper.getBootstrapServers();
    if (bootstrapServers == null || bootstrapServers.isBlank()) {
      log.debug(
          "No bootstrap.servers configured — skipping ParticipantStatusTopicStore initialisation");
      return;
    }
    String topic =
        taktPropertiesHelper.getPrefixedTopicName(
            io.taktx.Topics.PARTICIPANT_STATUS_TOPIC.getTopicName());
    try {
      Properties consumerProps =
          taktPropertiesHelper.getKafkaConsumerProperties(
              "participant-status-store-" + ProcessHandle.current().pid(),
              org.apache.kafka.common.serialization.StringDeserializer.class,
              org.apache.kafka.common.serialization.ByteArrayDeserializer.class,
              "earliest");
      participantStatusStore = new ClientParticipantStatusStore();
      participantStatusTopicStore =
          new ParticipantStatusTopicStore(
              consumerProps, topic, participantStatusStore, this::notifyParticipantStatusConsumers);
      participantStatusTopicStore.awaitReady(java.time.Duration.ofSeconds(10));
      log.info(
          "✅ ParticipantStatusTopicStore ready — currentParticipantCount={}",
          participantStatusStore.currentSnapshot(System.currentTimeMillis()).size());
    } catch (Exception e) {
      participantStatusStore = null;
      if (participantStatusTopicStore != null) {
        try {
          participantStatusTopicStore.close();
        } catch (Exception closeEx) {
          log.debug("Error closing failed ParticipantStatusTopicStore", closeEx);
        }
      }
      participantStatusTopicStore = null;
      log.warn(
          "ParticipantStatusTopicStore initialisation failed — current participant snapshot will be empty: {}",
          e.getMessage());
    }
  }

  private void initSecurityEventStore() {
    if (securityEventStore != null && securityEventTopicStore != null) {
      return;
    }
    String bootstrapServers = taktPropertiesHelper.getBootstrapServers();
    if (bootstrapServers == null || bootstrapServers.isBlank()) {
      log.debug(
          "No bootstrap.servers configured — skipping SecurityEventTopicStore initialisation");
      return;
    }
    String topic =
        taktPropertiesHelper.getPrefixedTopicName(
            io.taktx.Topics.SECURITY_EVENTS_TOPIC.getTopicName());
    try {
      Properties consumerProps =
          taktPropertiesHelper.getKafkaConsumerProperties(
              "security-event-store-" + ProcessHandle.current().pid(),
              org.apache.kafka.common.serialization.StringDeserializer.class,
              org.apache.kafka.common.serialization.ByteArrayDeserializer.class,
              "latest");
      if (securityEventStore == null) {
        securityEventStore = new ClientSecurityEventStore(DEFAULT_SECURITY_EVENT_HISTORY_SIZE);
      }
      securityEventTopicStore =
          new SecurityEventTopicStore(
              consumerProps,
              topic,
              securityEventStore,
              this::notifySecurityEventConsumers,
              DEFAULT_SECURITY_EVENT_HISTORY_SIZE);
      securityEventTopicStore.awaitReady(java.time.Duration.ofSeconds(10));
      log.info(
          "✅ SecurityEventTopicStore ready — recentEventCount={}",
          securityEventStore.snapshot().size());
    } catch (Exception e) {
      securityEventStore = null;
      if (securityEventTopicStore != null) {
        try {
          securityEventTopicStore.close();
        } catch (Exception closeEx) {
          log.debug("Error closing failed SecurityEventTopicStore", closeEx);
        }
      }
      securityEventTopicStore = null;
      log.warn(
          "SecurityEventTopicStore initialisation failed — recent security event history will be empty: {}",
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
      throw new IllegalStateException("Failed to publish license", e);
    }
  }

  /**
   * Publishes cluster-wide runtime configuration to the {@code taktx-configuration} compacted topic
   * under key {@code "config"}.
   *
   * @param configuration runtime configuration to publish
   */
  public void publishGlobalConfig(GlobalConfigurationDTO configuration) {
    publishGlobalConfig(taktPropertiesHelper.getTaktProperties(), configuration);
  }

  /**
   * Static convenience overload for publishing runtime configuration without a running client
   * instance.
   *
   * @param properties client/cluster properties used to resolve Kafka connectivity and topic prefix
   * @param configuration runtime configuration to publish
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
          ConfigurationProtoMapper.toProto(buildConfigurationEvent(configuration)).toByteArray();
      producer.send(
          new org.apache.kafka.clients.producer.ProducerRecord<>(
              topic, CONFIGURATION_RECORD_KEY, valueBytes));
      producer.flush();
      log.info("✅ Global configuration published to configuration topic: topic={}", topic);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to publish global configuration", e);
    }
  }

  /** Publishes namespace security policy to the compacted {@code taktx-security-policy} topic. */
  public static void publishNamespaceSecurityPolicy(
      Properties properties, NamespaceSecurityPolicyDTO policy) {
    if (policy == null) {
      throw new IllegalArgumentException("policy must not be null");
    }
    TaktPropertiesHelper helper = new TaktPropertiesHelper(properties);
    String topic =
        helper.getPrefixedTopicName(io.taktx.Topics.SECURITY_POLICY_TOPIC.getTopicName());

    java.util.Properties producerProps = helper.getKafkaProducerProperties();
    producerProps.put("max.block.ms", "5000");
    producerProps.put("delivery.timeout.ms", "5000");
    producerProps.put("request.timeout.ms", "3000");

    try (org.apache.kafka.clients.producer.KafkaProducer<String, byte[]> producer =
        new org.apache.kafka.clients.producer.KafkaProducer<>(
            producerProps,
            new org.apache.kafka.common.serialization.StringSerializer(),
            new org.apache.kafka.common.serialization.ByteArraySerializer())) {
      byte[] valueBytes = NamespaceSecurityPolicyProtoMapper.toProto(policy).toByteArray();
      producer.send(
          new org.apache.kafka.clients.producer.ProducerRecord<>(
              topic, SECURITY_POLICY_RECORD_KEY, valueBytes));
      producer.flush();
      log.info("✅ Namespace security policy published to {}: mode={}", topic, policy.getMode());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to publish namespace security policy", e);
    }
  }

  /**
   * Clears the namespace security policy by publishing a tombstone to the compacted policy topic.
   */
  public static void clearNamespaceSecurityPolicy(Properties properties) {
    TaktPropertiesHelper helper = new TaktPropertiesHelper(properties);
    String topic =
        helper.getPrefixedTopicName(io.taktx.Topics.SECURITY_POLICY_TOPIC.getTopicName());

    java.util.Properties producerProps = helper.getKafkaProducerProperties();
    producerProps.put("max.block.ms", "5000");
    producerProps.put("delivery.timeout.ms", "5000");
    producerProps.put("request.timeout.ms", "3000");

    try (org.apache.kafka.clients.producer.KafkaProducer<String, byte[]> producer =
        new org.apache.kafka.clients.producer.KafkaProducer<>(
            producerProps,
            new org.apache.kafka.common.serialization.StringSerializer(),
            new org.apache.kafka.common.serialization.ByteArraySerializer())) {
      producer.send(
          new org.apache.kafka.clients.producer.ProducerRecord<>(
              topic, SECURITY_POLICY_RECORD_KEY, null));
      producer.flush();
      log.info("✅ Namespace security policy cleared via tombstone: topic={}", topic);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to clear namespace security policy", e);
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
   */
  public void publishSigningKey(String keyId, String publicKeyBase64) {
    publishSigningKey(keyId, publicKeyBase64, "Ed25519");
  }

  /**
   * Publishes a public key with an explicit algorithm label such as {@code Ed25519} or {@code RSA}.
   *
   * @param keyId unique identifier for this key
   * @param publicKeyBase64 X.509 DER public key, base64-encoded
   * @param algorithm key algorithm label such as {@code Ed25519} or {@code RSA}
   */
  public void publishSigningKey(String keyId, String publicKeyBase64, String algorithm) {
    publishSigningKey(keyId, publicKeyBase64, algorithm, KeyRole.CLIENT);
  }

  /**
   * Publishes a public key with an explicit algorithm label and role. Use {@link KeyRole#CLIENT}
   * for worker/client keys (the default). Reserved overload for platform tooling that publishes
   * platform-level keys.
   *
   * @param keyId unique identifier for this key
   * @param publicKeyBase64 X.509 DER public key, base64-encoded
   * @param algorithm key algorithm label such as {@code Ed25519} or {@code RSA}
   * @param role trust role under which the key should be published
   */
  public void publishSigningKey(
      String keyId, String publicKeyBase64, String algorithm, KeyRole role) {
    publishSigningKey(keyId, publicKeyBase64, algorithm, role, null);
  }

  /**
   * Publishes a public key with an explicit algorithm, role, and platform countersignature.
   *
   * <p>Use this overload in <em>anchored mode</em> ({@code TAKTX_PLATFORM_PUBLIC_KEY} is set on the
   * engine). The {@code registrationSignature} must be the base64-encoded RSA/SHA-256 signature
   * produced by the platform root private key over the key's canonical payload:
   *
   * <pre>{@code keyId|publicKeyBase64|algorithm|role}</pre>
   *
   * <p>Generate with {@code scripts/generate_trust_anchor.sh --worker}. Without a valid
   * countersignature, the engine will reject all commands signed by this worker key when anchored
   * mode is active.
   *
   * @param keyId unique identifier for this key
   * @param publicKeyBase64 X.509 DER public key, base64-encoded
   * @param algorithm key algorithm label such as {@code Ed25519} or {@code RSA}
   * @param role trust role under which the key should be published
   * @param registrationSignature base64-encoded RSA/SHA-256 countersignature, or {@code null} in
   *     community mode
   */
  public void publishSigningKey(
      String keyId,
      String publicKeyBase64,
      String algorithm,
      KeyRole role,
      @Nullable String registrationSignature) {
    new SigningKeyRegistrar(taktPropertiesHelper)
        .publishPublicKey(keyId, publicKeyBase64, algorithm, role, registrationSignature);
    log.info(
        "✅ Signing key published: keyId={} algorithm={} role={} countersigned={}",
        keyId,
        algorithm,
        role,
        registrationSignature != null);
  }

  /**
   * Static convenience overload for callers that do not yet have a running {@link TaktXClient}
   * instance — e.g. test setup code or platform bootstrap that runs before the client is started.
   *
   * @param properties must contain {@code bootstrap.servers} and {@code taktx.engine.namespace} —
   *     both are required
   * @param keyId unique identifier for this key
   * @param publicKeyBase64 X.509 DER public key, base64-encoded
   */
  public static void publishSigningKey(
      Properties properties, String keyId, String publicKeyBase64) {
    publishSigningKey(properties, keyId, publicKeyBase64, "Ed25519");
  }

  /**
   * Static convenience overload with an explicit algorithm label such as {@code Ed25519} or {@code
   * RSA}.
   *
   * @param properties client/cluster properties used to resolve Kafka connectivity and topic prefix
   * @param keyId unique identifier for this key
   * @param publicKeyBase64 X.509 DER public key, base64-encoded
   * @param algorithm key algorithm label such as {@code Ed25519} or {@code RSA}
   */
  public static void publishSigningKey(
      Properties properties, String keyId, String publicKeyBase64, String algorithm) {
    publishSigningKey(properties, keyId, publicKeyBase64, algorithm, KeyRole.CLIENT);
  }

  /**
   * Static convenience overload with an explicit algorithm and role.
   *
   * @param properties client/cluster properties used to resolve Kafka connectivity and topic prefix
   * @param keyId unique identifier for this key
   * @param publicKeyBase64 X.509 DER public key, base64-encoded
   * @param algorithm key algorithm label such as {@code Ed25519} or {@code RSA}
   * @param role trust role under which the key should be published
   */
  public static void publishSigningKey(
      Properties properties, String keyId, String publicKeyBase64, String algorithm, KeyRole role) {
    publishSigningKey(properties, keyId, publicKeyBase64, algorithm, role, null);
  }

  /**
   * Static convenience overload with an explicit algorithm, role, and platform countersignature.
   *
   * <p>Use this overload in anchored mode. The {@code registrationSignature} must be the
   * base64-encoded RSA/SHA-256 signature produced by the platform root private key over {@code
   * keyId|publicKeyBase64|algorithm|role}. Pass {@code null} in community mode.
   *
   * @param properties client/cluster properties used to resolve Kafka connectivity and topic prefix
   * @param keyId unique identifier for this key
   * @param publicKeyBase64 X.509 DER public key, base64-encoded
   * @param algorithm key algorithm label such as {@code Ed25519} or {@code RSA}
   * @param role trust role under which the key should be published
   * @param registrationSignature base64-encoded RSA/SHA-256 countersignature, or {@code null} in
   *     community mode
   */
  public static void publishSigningKey(
      Properties properties,
      String keyId,
      String publicKeyBase64,
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
        algorithm,
        role,
        registrationSignature);
    boolean countersigned = registrationSignature != null && !registrationSignature.isBlank();
    if (countersigned) {
      log.info(
          "✅ Signing key published: keyId={} algorithm={} role={} countersigned=true"
              + " trustMode=anchored-ready",
          keyId,
          algorithm,
          role);
    } else {
      log.warn(
          "Signing key published without a registration signature: keyId={} algorithm={}"
              + " role={} countersigned=false trustMode=community-only. Anchored engines will"
              + " reject this key; Kafka ACLs must protect taktx-signing-keys in community mode.",
          keyId,
          algorithm,
          role);
    }
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
    SigningIdentity identity =
        signingIdentitySource != null ? signingIdentitySource.currentIdentity() : null;
    trackObservedWorkerIdentity(identity);
    return identity;
  }

  void refreshWorkerSigningFunctionRegistration() {
    // Mode is startup-static: only register if anchored and the capability is declared.
    if (!anchored || !declaresCapability(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)) {
      return;
    }
    SigningIdentity identity = currentSigningIdentity();
    if (identity == null) {
      String sourceType =
          signingIdentitySource != null ? signingIdentitySource.getSourceType() : "none";
      logWorkerSigningRegistrationState(
          "waiting-for-identity:" + sourceType,
          "Worker response signing: anchored mode but no signing identity available from source={}",
          sourceType);
      return;
    }
    SigningServiceHolder.SigningFunction existing = SigningServiceHolder.get();
    if (existing == null) {
      SigningServiceHolder.set(globalWorkerSigningFunction);
      globalWorkerSigningFunctionRegistered = true;
    } else if (existing == globalWorkerSigningFunction) {
      globalWorkerSigningFunctionRegistered = true;
    } else {
      globalWorkerSigningFunctionRegistered = false;
    }
    logWorkerSigningRegistrationState(
        "active:" + identity.getKeyId(),
        "Worker response signing registered — source={} keyId={}",
        signingIdentitySource.getSourceType(),
        identity.getKeyId());
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
    if (!anchored) {
      return false;
    }
    if (!identity.hasPublicKey()) {
      log.debug(
          "No public key in signing identity — skipping worker key publication"
              + " (set TAKTX_SIGNING_PUBLIC_KEY or taktx.signing.public-key)");
      return true;
    }
    String descriptor = workerIdentityPublicationDescriptor(identity);
    if (descriptor.equals(publishedWorkerIdentityDescriptor)) {
      return true;
    }
    SigningKeyDTO previousKeyRecord = publishedWorkerKeyRecord;
    try {
      publishSigningKey(
          identity.getKeyId(),
          identity.getPublicKeyBase64(),
          identity.getAlgorithm(),
          KeyRole.CLIENT,
          workerKeyRegistrationSignature);
      publishedWorkerKeyId = identity.getKeyId();
      publishedWorkerIdentityDescriptor = descriptor;
      publishedWorkerKeyRecord =
          SigningKeyDTO.builder()
              .keyId(identity.getKeyId())
              .publicKeyBase64(identity.getPublicKeyBase64())
              .algorithm(identity.getAlgorithm())
              .role(KeyRole.CLIENT)
              .registrationSignature(workerKeyRegistrationSignature)
              .status(SigningKeyDTO.KeyStatus.ACTIVE)
              .build();
      retireOldWorkerKey(previousKeyRecord, identity.getKeyId());
      return true;
    } catch (Exception e) {
      log.error("Failed to publish worker signing key: {}", e.getMessage(), e);
      return false;
    }
  }

  private void retireOldWorkerKey(@Nullable SigningKeyDTO previousKey, String newKeyId) {
    if (previousKey == null || previousKey.getKeyId().equals(newKeyId)) {
      return;
    }
    try {
      new SigningKeyRegistrar(taktPropertiesHelper)
          .publishKeyStatusChange(
              SigningKeyDTO.builder()
                  .keyId(previousKey.getKeyId())
                  .publicKeyBase64(previousKey.getPublicKeyBase64())
                  .algorithm(previousKey.getAlgorithm())
                  .role(previousKey.effectiveRole())
                  .registrationSignature(previousKey.getRegistrationSignature())
                  .status(SigningKeyDTO.KeyStatus.TRUSTED)
                  .build());
      log.info(
          "Previous worker key retired to TRUSTED: previousKeyId={} newKeyId={}",
          previousKey.getKeyId(),
          newKeyId);
    } catch (Exception e) {
      log.warn(
          "Failed to retire previous worker key to TRUSTED: previousKeyId={} — {}",
          previousKey.getKeyId(),
          e.getMessage());
    }
  }

  private void trackObservedWorkerIdentity(@Nullable SigningIdentity identity) {
    String descriptor = workerIdentityRuntimeDescriptor(identity);
    String previousDescriptor = activeWorkerIdentityDescriptor;
    if (Objects.equals(previousDescriptor, descriptor)) {
      return;
    }
    activeWorkerIdentityDescriptor = descriptor;
    if (previousDescriptor == null || descriptor == null) {
      return;
    }
    recordWorkerIdentityRotation(previousDescriptor, descriptor);
  }

  private void recordWorkerIdentityRotation(String previousDescriptor, String newDescriptor) {
    boolean expectedLiveRotation =
        signingIdentitySource != null && signingIdentitySource.supportsLiveRotation();
    String code =
        expectedLiveRotation
            ? SecurityPostureIssueCodes.SIGNING_IDENTITY_ROTATED
            : SecurityPostureIssueCodes.UNEXPECTED_SIGNING_IDENTITY_CHURN;
    SecurityEventSeverity severity =
        expectedLiveRotation ? SecurityEventSeverity.INFO : SecurityEventSeverity.WARNING;
    String previousKeyId = descriptorPart(previousDescriptor, 0);
    String newKeyId = descriptorPart(newDescriptor, 0);
    if (expectedLiveRotation) {
      log.info(
          "Worker signing identity rotated at runtime — source={} previousKeyId={} newKeyId={}",
          signingIdentitySource != null ? signingIdentitySource.getSourceType() : "none",
          previousKeyId,
          newKeyId);
    } else {
      log.warn(
          "Unexpected worker signing identity churn detected — source={} previousKeyId={} newKeyId={}",
          signingIdentitySource != null ? signingIdentitySource.getSourceType() : "none",
          previousKeyId,
          newKeyId);
    }
    appendLocalSecurityEvent(
        SecurityEventDTO.builder()
            .eventType(SecurityEventType.POLICY_CHANGE)
            .severity(severity)
            .occurredAtMs(System.currentTimeMillis())
            .namespace(taktPropertiesHelper.getNamespace())
            .participantId(participantDescriptor.participantId())
            .participantInstanceId(participantInstanceId())
            .code(code)
            .message(
                expectedLiveRotation
                    ? "Worker signing identity rotated and trust-registry publication will refresh"
                    : "Worker signing identity changed unexpectedly and trust-registry publication will refresh")
            .metadata(
                Map.of(
                    "sourceType",
                    signingIdentitySource != null ? signingIdentitySource.getSourceType() : "none",
                    "previousKeyId",
                    previousKeyId,
                    "newKeyId",
                    newKeyId,
                    "restartStable",
                    String.valueOf(
                        signingIdentitySource != null && signingIdentitySource.isRestartStable()),
                    "supportsLiveRotation",
                    String.valueOf(expectedLiveRotation)))
            .build());
  }

  private void appendLocalSecurityEvent(SecurityEventDTO event) {
    if (securityEventStore == null) {
      securityEventStore = new ClientSecurityEventStore(DEFAULT_SECURITY_EVENT_HISTORY_SIZE);
    }
    securityEventStore.append(event);
    notifySecurityEventConsumers(event);
  }

  private static @Nullable String workerIdentityRuntimeDescriptor(
      @Nullable SigningIdentity identity) {
    if (identity == null) {
      return null;
    }
    return String.join(
        "|",
        safe(identity.getKeyId()),
        safe(identity.getPublicKeyBase64()),
        safe(identity.getAlgorithm()));
  }

  private String workerIdentityPublicationDescriptor(SigningIdentity identity) {
    return String.join(
        "|",
        safe(identity.getKeyId()),
        safe(identity.getPublicKeyBase64()),
        safe(identity.getAlgorithm()),
        KeyRole.CLIENT.name(),
        safe(workerKeyRegistrationSignature));
  }

  private String participantInstanceId() {
    return participantDescriptor.participantId() + "#" + ProcessHandle.current().pid();
  }

  private static String descriptorPart(String descriptor, int index) {
    if (descriptor == null) {
      return "<none>";
    }
    String[] parts = descriptor.split("\\|", -1);
    if (index < 0 || index >= parts.length) {
      return "<none>";
    }
    return parts[index] == null || parts[index].isBlank() ? "<none>" : parts[index];
  }

  private static String safe(@Nullable String value) {
    return value != null ? value : "";
  }

  private String signWorkerPayload(byte[] payload) {
    if (!anchored || !declaresCapability(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)) {
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

  private void ensureProtectedDataPlaneOperationAllowed(
      ProtectedClientDataPlaneOperation operation, @Nullable String explicitAuthorizationToken) {
    protectedDataPlaneParticipationGuard.check(operation, explicitAuthorizationToken);
  }

  private void ensureParticipantCapability(
      ParticipantCapability capability, String operationDescription) {
    if (!declaresCapability(capability)) {
      throw new IllegalStateException(
          "TaktXClient participant descriptor "
              + participantDescriptor.participantId()
              + " does not declare "
              + capability
              + " and therefore cannot "
              + operationDescription);
    }
  }

  private boolean declaresCapability(ParticipantCapability capability) {
    return participantDescriptor.capabilities().contains(capability);
  }

  private boolean hasPublishedSigningCapability() {
    if (!anchored || !declaresCapability(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT)) {
      return false;
    }
    SigningIdentity identity = currentSigningIdentity();
    if (identity == null) {
      return false;
    }
    return ensureWorkerKeyPublished(identity);
  }

  @Nullable
  private String currentSigningKeyIdString() {
    SigningIdentity identity = currentSigningIdentity();
    return identity != null ? identity.getKeyId() : null;
  }

  /** Whether a signing identity with a private key is currently loaded from the identity source. */
  private boolean clientSigningConfigured() {
    SigningIdentity identity = currentSigningIdentity();
    return identity != null
        && identity.getPrivateKeyBase64() != null
        && !identity.getPrivateKeyBase64().isBlank();
  }

  /** Whether the current signing key is visible (non-revoked) in the signing-keys registry. */
  private boolean clientSigningKeyPublished() {
    SigningIdentity identity = currentSigningIdentity();
    if (identity == null || signingKeysStore == null) {
      return false;
    }
    return signingKeysStore.getPublicKeyBase64(identity.getKeyId()) != null;
  }

  /** Whether the current signing key entry carries a platform registration countersignature. */
  private boolean clientSigningKeyCountersigned() {
    SigningIdentity identity = currentSigningIdentity();
    if (identity == null || signingKeysStore == null) {
      return false;
    }
    SigningKeyDTO entry = signingKeysStore.get(identity.getKeyId());
    return entry != null
        && entry.getRegistrationSignature() != null
        && !entry.getRegistrationSignature().isBlank();
  }

  private @Nullable String resolvePlatformPublicKey() {
    return resolvePlatformPublicKey(taktPropertiesHelper.getTaktProperties());
  }

  private static @Nullable String resolvePlatformPublicKey(Properties properties) {
    String configured = properties.getProperty("taktx.platform.public-key");
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    configured = System.getProperty("taktx.platform.public-key");
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    configured = System.getenv("TAKTX_PLATFORM_PUBLIC_KEY");
    return configured != null && !configured.isBlank() ? configured : null;
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
    this.participantStatusPublisher.stop();
    this.processDefinitionConsumer.stop();
    this.externalTaskTriggerTopicConsumer.stop();
    this.userTaskTriggerTopicConsumer.stop();
    this.processInstanceUpdateConsumer.stop();
    this.xmlByProcessDefinitionIdConsumer.stop();
    this.xmlByDmnDefinitionIdConsumer.stop();
    if (dlqEntryConsumer != null) {
      dlqEntryConsumer.stop();
      dlqEntryConsumer = null;
    }
    if (dlqReplayResultConsumer != null) {
      dlqReplayResultConsumer.stop();
      dlqReplayResultConsumer = null;
    }
    if (dlqReplayCommandProducer != null) {
      dlqReplayCommandProducer.close();
      dlqReplayCommandProducer = null;
    }
    if (signingKeysStore != null) {
      SigningKeysStoreHolder.clear();
      signingKeysStore.close();
      signingKeysStore = null;
    }
    if (runtimeConfigurationStore != null) {
      runtimeConfigurationStore.close();
      runtimeConfigurationStore = null;
    }
    if (participantStatusTopicStore != null) {
      participantStatusTopicStore.close();
      participantStatusTopicStore = null;
    }
    participantStatusStore = null;
    if (securityEventTopicStore != null) {
      securityEventTopicStore.close();
      securityEventTopicStore = null;
    }
    securityEventStore = null;
    processInstanceResponder.close();
    processInstanceProducer.close();
    messageEventSender.close();
    signalSender.close();
    processDefinitionDeployer.close();
    dmnDefinitionDeployer.close();
    externalTaskTopicRequester.close();
    publishedWorkerKeyId = null;
    publishedWorkerIdentityDescriptor = null;
    publishedWorkerKeyRecord = null;
    activeWorkerIdentityDescriptor = null;
    workerSigningRegistrationState = "uninitialized";
    RuntimeConfigurationHolder.clear();
    if (globalWorkerSigningFunctionRegistered
        && SigningServiceHolder.get() == globalWorkerSigningFunction) {
      SigningServiceHolder.clear();
    }
    globalWorkerSigningFunctionRegistered = false;
  }

  private Map<String, ParticipantStatusDTO> currentParticipantStatusSnapshot() {
    if (participantStatusStore == null) {
      return Map.of();
    }
    return participantStatusStore.currentSnapshot(System.currentTimeMillis());
  }

  private List<SecurityEventDTO> currentSecurityEventSnapshot() {
    return securityEventStore != null ? securityEventStore.snapshot() : List.of();
  }

  private void registerNamespaceSecurityPolicyConsumer(NamespaceSecurityPolicyConsumer consumer) {
    namespaceSecurityPolicyConsumers.add(consumer);
  }

  private void registerParticipantStatusConsumer(ParticipantStatusConsumer consumer) {
    participantStatusConsumers.add(consumer);
  }

  private void registerSecurityEventConsumer(SecurityEventConsumer consumer) {
    securityEventConsumers.add(consumer);
  }

  private void notifyParticipantStatusConsumers() {
    Map<String, ParticipantStatusDTO> snapshot = currentParticipantStatusSnapshot();
    for (ParticipantStatusConsumer consumer : participantStatusConsumers) {
      try {
        consumer.accept(snapshot);
      } catch (Exception e) {
        log.warn("Participant status consumer callback failed: {}", e.getMessage());
      }
    }
  }

  private void notifySecurityEventConsumers(SecurityEventDTO event) {
    for (SecurityEventConsumer consumer : securityEventConsumers) {
      try {
        consumer.accept(event);
      } catch (Exception e) {
        log.warn("Security event consumer callback failed: {}", e.getMessage());
      }
    }
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

  /**
   * Starts a new process instance of a specific process-definition version.
   *
   * @param process the ID of the process definition to start
   * @param version explicit process-definition version, or {@code -1} for latest
   * @param variables the initial variables for the process instance
   * @return the UUID of the started process instance
   */
  public UUID startProcess(String process, int version, VariablesDTO variables) {
    return processInstanceProducer.startProcess(process, version, variables);
  }

  /**
   * Starts a new process instance with a Platform Service authorization token.
   *
   * @param process the ID of the process definition to start
   * @param version explicit process-definition version, or {@code -1} for latest
   * @param variables the initial variables for the process instance
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   * @return the UUID of the started process instance
   */
  public UUID startProcess(
      String process, int version, VariablesDTO variables, @Nullable String authorizationToken) {
    return processInstanceProducer.startProcess(process, version, variables, authorizationToken);
  }

  /**
   * Starts a new process instance with optional business metadata (latest version).
   *
   * @param process the ID of the process definition to start
   * @param variables the initial variables for the process instance
   * @param businessKey optional business identifier for this instance; trimmed, empty treated as
   *     {@code null}, max 512 characters
   * @param tags optional immutable operational labels; normalised to lowercase, max 20 tags, max 64
   *     characters each, allowed characters: {@code a-z 0-9 . _ -}
   * @return the UUID of the started process instance
   */
  public UUID startProcess(
      String process, VariablesDTO variables, @Nullable String businessKey, Set<String> tags) {
    return processInstanceProducer.startProcess(process, variables, businessKey, tags);
  }

  /**
   * Starts a new process instance with optional business metadata and a Platform Service
   * authorization token.
   *
   * @param process the ID of the process definition to start
   * @param version explicit process-definition version, or {@code -1} for latest
   * @param variables the initial variables for the process instance
   * @param businessKey optional business identifier; see {@link #startProcess(String, VariablesDTO,
   *     String, Set)} for rules
   * @param tags optional immutable operational labels; see above for rules
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   * @return the UUID of the started process instance
   */
  public UUID startProcess(
      String process,
      int version,
      VariablesDTO variables,
      @Nullable String businessKey,
      Set<String> tags,
      @Nullable String authorizationToken) {
    return processInstanceProducer.startProcess(
        process, version, variables, businessKey, tags, authorizationToken);
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

  /** Deploys all classes annotated with @Deployment found in the classpath. */
  public void deployTaktDeploymentAnnotatedClasses() {
    Set<Deployment> deployments = AnnotationScanner.findTaktDeployments();
    for (Deployment annotation : deployments) {
      for (String resource : annotation.resources()) {
        log.info("Deploying process definition from resource {}", resource);
        processDefinitionDeployer.deployResource(resource);
      }
      for (String dmnResource : annotation.dmnResources()) {
        log.info("Deploying DMN definition from resource {}", dmnResource);
        dmnDefinitionDeployer.deployResource(dmnResource);
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
   * Completes a user task using the generated process-instance trigger publisher.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active user task.
   * @param variables The variables to merge on completion.
   */
  public void completeUserTask(
      UUID processInstanceId, List<Long> elementInstanceIdPath, VariablesDTO variables) {
    processInstanceResponder.completeUserTask(processInstanceId, elementInstanceIdPath, variables);
  }

  /**
   * Completes a user task, attaching a Platform Service authorization token.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active user task.
   * @param variables The variables to merge on completion.
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void completeUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    processInstanceResponder.completeUserTask(
        processInstanceId, elementInstanceIdPath, variables, authorizationToken);
  }

  /**
   * Completes a user task with a BPMN error using the generated process-instance trigger publisher.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active user task.
   * @param code The BPMN error code.
   * @param message The BPMN error message.
   * @param variables The variables to merge with the BPMN error response.
   */
  public void errorUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    processInstanceResponder.errorUserTask(
        processInstanceId, elementInstanceIdPath, code, message, variables);
  }

  /**
   * Completes a user task with a BPMN error, attaching a Platform Service authorization token.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active user task.
   * @param code The BPMN error code.
   * @param message The BPMN error message.
   * @param variables The variables to merge with the BPMN error response.
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void errorUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    processInstanceResponder.errorUserTask(
        processInstanceId, elementInstanceIdPath, code, message, variables, authorizationToken);
  }

  /**
   * Completes a user task with a BPMN escalation using the generated process-instance trigger
   * publisher.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active user task.
   * @param code The BPMN escalation code.
   * @param message The BPMN escalation message.
   * @param variables The variables to merge with the BPMN escalation response.
   */
  public void escalateUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    processInstanceResponder.escalateUserTask(
        processInstanceId, elementInstanceIdPath, code, message, variables);
  }

  /**
   * Completes a user task with a BPMN escalation, attaching a Platform Service authorization token.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active user task.
   * @param code The BPMN escalation code.
   * @param message The BPMN escalation message.
   * @param variables The variables to merge with the BPMN escalation response.
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void escalateUserTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    processInstanceResponder.escalateUserTask(
        processInstanceId, elementInstanceIdPath, code, message, variables, authorizationToken);
  }

  /**
   * Completes an external task using the generated process-instance trigger publisher.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active external
   *     task.
   * @param variables The variables to merge on completion.
   */
  public void completeExternalTask(
      UUID processInstanceId, List<Long> elementInstanceIdPath, VariablesDTO variables) {
    processInstanceResponder.completeExternalTask(
        processInstanceId, elementInstanceIdPath, variables);
  }

  /**
   * Completes an external task, attaching a Platform Service authorization token.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active external
   *     task.
   * @param variables The variables to merge on completion.
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void completeExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    processInstanceResponder.completeExternalTask(
        processInstanceId, elementInstanceIdPath, variables, authorizationToken);
  }

  /**
   * Completes an external task with a BPMN error using the generated process-instance trigger
   * publisher.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active external
   *     task.
   * @param code The BPMN error code.
   * @param message The BPMN error message.
   * @param variables The variables to merge with the BPMN error response.
   */
  public void errorExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    processInstanceResponder.errorExternalTask(
        processInstanceId, elementInstanceIdPath, code, message, variables);
  }

  /**
   * Completes an external task with a BPMN error, attaching a Platform Service authorization token.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active external
   *     task.
   * @param code The BPMN error code.
   * @param message The BPMN error message.
   * @param variables The variables to merge with the BPMN error response.
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void errorExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    processInstanceResponder.errorExternalTask(
        processInstanceId, elementInstanceIdPath, code, message, variables, authorizationToken);
  }

  /**
   * Completes an external task with a BPMN escalation using the generated process-instance trigger
   * publisher.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active external
   *     task.
   * @param code The BPMN escalation code.
   * @param message The BPMN escalation message.
   * @param variables The variables to merge with the BPMN escalation response.
   */
  public void escalateExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables) {
    processInstanceResponder.escalateExternalTask(
        processInstanceId, elementInstanceIdPath, code, message, variables);
  }

  /**
   * Completes an external task with a BPMN escalation, attaching a Platform Service authorization
   * token.
   *
   * @param processInstanceId The UUID of the process instance.
   * @param elementInstanceIdPath The path of element instance IDs leading to the active external
   *     task.
   * @param code The BPMN escalation code.
   * @param message The BPMN escalation message.
   * @param variables The variables to merge with the BPMN escalation response.
   * @param authorizationToken RS256 JWT from the Platform Service, or {@code null}
   */
  public void escalateExternalTask(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String code,
      String message,
      VariablesDTO variables,
      @Nullable String authorizationToken) {
    processInstanceResponder.escalateExternalTask(
        processInstanceId, elementInstanceIdPath, code, message, variables, authorizationToken);
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
   * @param activeProcessInstanceId the UUID of the active process instance
   * @param elementInstanceIdPath the path of element instance IDs leading to the element to abort
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
   * Retrieves the XML of a DMN definition by its key.
   *
   * @param dmnDefinitionKey The key of the DMN definition.
   * @return The XML of the DMN definition, or {@code null} if not yet received.
   * @throws IOException If an error occurs while retrieving the XML.
   */
  public String getDmnDefinitionXml(DmnDefinitionKey dmnDefinitionKey) throws IOException {
    return this.xmlByDmnDefinitionIdConsumer.getDmnDefinitionXml(dmnDefinitionKey);
  }

  /**
   * Returns the {@link DmnDefinitionKey} of the DMN file that contains the given decision ID.
   *
   * <p>The index is built by replaying the {@code xml-by-dmn-definition-id} topic from the earliest
   * offset on every client start, so it is complete once the initial replay has finished.
   *
   * @param decisionId the decision ID to look up (e.g. {@code "discountDecision"})
   * @return an Optional containing the key, or empty if the decision is not yet known
   */
  public Optional<DmnDefinitionKey> getDmnDefinitionKeyForDecision(String decisionId) {
    return this.xmlByDmnDefinitionIdConsumer.getDefinitionKeyForDecision(decisionId);
  }

  /**
   * Returns a read-only snapshot of the full {@code decisionId → DmnDefinitionKey} index. Useful
   * for console views that list all known decision tables grouped by their DMN file.
   *
   * @return unmodifiable map of decision ID to the DMN definition key of its containing file
   */
  public Map<String, DmnDefinitionKey> getDmnDecisionIndex() {
    return this.xmlByDmnDefinitionIdConsumer.getDecisionIndex();
  }

  /**
   * Sends a signal event to the engine.
   *
   * @param signalName The name of the signal to send.
   */
  public void sendSignal(String signalName) {
    this.signalSender.sendMSignal(new SignalDTO(signalName));
  }

  // ── DLQ API ──────────────────────────────────────────────────────────────────

  /**
   * Registers a handler that receives every {@link DlqEnvelope} from the {@code dlq} topic,
   * resuming from the last committed offset for this consumer group.
   *
   * <p>The first call starts the background polling loop on a virtual thread. Additional handlers
   * for the same client instance are added to the same loop.
   *
   * <p>This is a <em>Community</em>-tier feature — any application with proper Kafka ACLs can
   * consume the DLQ topic directly.
   *
   * @param groupId Kafka consumer group ID (determines offset tracking and parallelism)
   * @param handler callback invoked for each {@link DlqEnvelope} (runs on the polling thread)
   */
  public void registerDlqEntryConsumer(
      String groupId, java.util.function.Consumer<DlqEnvelope> handler) {
    dlqEntryConsumerInstance().registerConsumer(groupId, handler);
  }

  /**
   * Registers a handler that receives every {@link DlqEnvelope} from the {@code dlq} topic with
   * explicit start-from-beginning control.
   *
   * @param groupId Kafka consumer group ID
   * @param handler callback invoked for each envelope
   * @param startFromEarliest when {@code true}, seeks every assigned partition to offset 0 after
   *     the first rebalance, guaranteeing a full-history replay
   */
  public void registerDlqEntryConsumer(
      String groupId, java.util.function.Consumer<DlqEnvelope> handler, boolean startFromEarliest) {
    dlqEntryConsumerInstance().registerConsumer(groupId, handler, startFromEarliest);
  }

  /**
   * Submits a {@link DlqReplayCommand} to the {@code dlq.replay} topic.
   *
   * <p>The engine's replay processor validates the command (destination safety, schema
   * compatibility, ENGINE signing) and either forwards the corrected record to the target ingress
   * topic or emits a failure result. The outcome is published to {@code dlq.replay-results} and can
   * be consumed via {@link #registerReplayResultConsumer}.
   *
   * <p>Use {@link io.taktx.client.dlq.DlqReplayCommandBuilder#from(DlqEnvelope)} to build a
   * well-formed command from an envelope.
   *
   * <p>This is a <em>Community</em>-tier feature.
   *
   * @param command the replay command to submit; must not be {@code null}
   */
  public void submitReplayCommand(DlqReplayCommand command) {
    dlqReplayCommandProducerInstance().submit(command);
  }

  /**
   * Registers a handler that receives {@link DlqReplayResult} records from the {@code
   * dlq.replay-results} topic.
   *
   * <p>Correlate results with submitted commands using {@link DlqReplayResult#getCorrectionId()}.
   * The {@link DlqReplayResult#getStatus()} field indicates {@code SUCCESS}, {@code
   * DRY_RUN_PASSED}, or {@code FAILED}.
   *
   * <p>This is a <em>Community</em>-tier feature.
   *
   * @param groupId Kafka consumer group ID
   * @param handler callback invoked for each replay result
   */
  public void registerReplayResultConsumer(
      String groupId, java.util.function.Consumer<DlqReplayResult> handler) {
    dlqReplayResultConsumerInstance().registerConsumer(groupId, handler);
  }

  private synchronized DlqEntryConsumer dlqEntryConsumerInstance() {
    if (dlqEntryConsumer == null) {
      dlqEntryConsumer =
          new DlqEntryConsumer(taktPropertiesHelper, Executors.newVirtualThreadPerTaskExecutor());
    }
    return dlqEntryConsumer;
  }

  private synchronized DlqReplayCommandProducer dlqReplayCommandProducerInstance() {
    if (dlqReplayCommandProducer == null) {
      dlqReplayCommandProducer = new DlqReplayCommandProducer(taktPropertiesHelper);
    }
    return dlqReplayCommandProducer;
  }

  private synchronized DlqReplayResultConsumer dlqReplayResultConsumerInstance() {
    if (dlqReplayResultConsumer == null) {
      dlqReplayResultConsumer =
          new DlqReplayResultConsumer(
              taktPropertiesHelper, Executors.newVirtualThreadPerTaskExecutor());
    }
    return dlqReplayResultConsumer;
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

  /** Returns the normalized participant descriptor configured for this client instance. */
  public SecurityParticipantDescriptor getParticipantDescriptor() {
    return participantDescriptor;
  }

  /** Returns the focused security facet for namespace security-policy mutation operations. */
  /** Returns the public observability facade for participant and key visibility. */
  public synchronized SecurityObservabilityClient observability() {
    if (securityObservabilityClient == null) {
      securityObservabilityClient =
          new SecurityObservabilityClient(
              this::currentParticipantStatusSnapshot,
              this::currentSecurityEventSnapshot,
              new SecurityObservabilityClient.ConsumerRegistrars(
                  this::registerNamespaceSecurityPolicyConsumer,
                  this::registerParticipantStatusConsumer,
                  this::registerSecurityEventConsumer),
              this::ensureObservabilityStoresInitialized);
    }
    return securityObservabilityClient;
  }

  /** Returns the focused runtime facet for deployment and protected runtime operations. */
  public synchronized RuntimeClient runtime() {
    if (runtimeClient == null) {
      runtimeClient = new RuntimeClient(this);
    }
    return runtimeClient;
  }

  /** Returns the focused workers facet for worker topic management and subscriptions. */
  public synchronized WorkersClient workers() {
    if (workersClient == null) {
      workersClient = new WorkersClient(this);
    }
    return workersClient;
  }

  /** Returns the focused DLQ facet for dead-letter observation and replay operations. */
  public synchronized DlqClient dlq() {
    if (dlqClient == null) {
      dlqClient = new DlqClient(this);
    }
    return dlqClient;
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
    private SecurityParticipantDescriptor participantDescriptor;
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
      SecurityParticipantDescriptor effectiveParticipantDescriptor =
          resolveParticipantDescriptor(properties);
      String effectiveRegistrationSignature = resolveWorkerKeyRegistrationSignature(properties);
      AtomicReference<SigningServiceHolder.SigningFunction> localSigningFunction =
          new AtomicReference<>();

      // Wrap the value serializer with ProtoSigningSerializer so signing happens in one pass.
      KafkaProducer<UUID, ProcessInstanceTriggerDTO> processInstanceTriggerEmitter =
          new KafkaProducer<>(
              taktPropertiesHelper.getKafkaProducerProperties(),
              new io.taktx.util.TaktUUIDSerializer(),
              new ProtoSigningSerializer<>(
                  ProcessInstanceTriggerProtoMapper::toProto, localSigningFunction::get));

      ProcessInstanceResponder externalTaskResponder =
          new ProcessInstanceResponder(
              taktPropertiesHelper,
              processInstanceTriggerEmitter,
              effectiveAuthorizationTokenProvider);

      ParameterResolverFactory clientParameterResolverFactory =
          this.parameterResolverFactory != null
              ? this.parameterResolverFactory
              : new DefaultParameterResolverFactory(externalTaskResponder);
      ResultProcessorFactory clientResultProcessorFactory =
          this.resultProcessorFactory != null
              ? this.resultProcessorFactory
              : new DefaultResultProcessorFactory();
      // Fail fast when anchored intent is detected but prerequisites are missing.
      boolean anchored = resolvePlatformPublicKey(properties) != null;
      if (anchored) {
        if (effectiveSigningIdentitySource == null
            || !effectiveSigningIdentitySource.isRestartStable()) {
          throw new IllegalStateException(
              "ANCHORED mode (TAKTX_PLATFORM_PUBLIC_KEY is set) requires a restart-stable signing"
                  + " identity source (env or file, not generated). Configure"
                  + " taktx.signing.identity-source=env or =file.");
        }
        if (effectiveSigningIdentitySource.currentIdentity() == null) {
          throw new IllegalStateException(
              "ANCHORED mode is configured but no signing identity is resolvable from source="
                  + effectiveSigningIdentitySource.getSourceType()
                  + ". Check TAKTX_SIGNING_PRIVATE_KEY, TAKTX_SIGNING_KEY_ID, etc.");
        }
        if (effectiveRegistrationSignature == null || effectiveRegistrationSignature.isBlank()) {
          throw new IllegalStateException(
              "ANCHORED mode requires taktx.signing.registration-signature"
                  + " (TAKTX_SIGNING_REGISTRATION_SIGNATURE) — the worker key must be"
                  + " countersigned by the platform root key.");
        }
      }

      TaktXClient client =
          new TaktXClient(
              taktPropertiesHelper,
              processInstanceTriggerEmitter,
              externalTaskResponder,
              clientParameterResolverFactory,
              clientResultProcessorFactory,
              effectiveParticipantDescriptor,
              effectiveSigningIdentitySource,
              effectiveAuthorizationTokenProvider,
              effectiveRegistrationSignature);
      localSigningFunction.set(client::signWorkerPayload);
      externalTaskResponder.setBeforeSendHook(client::refreshWorkerSigningFunctionRegistration);
      client.messageEventSender.setBeforeSendHook(client::refreshWorkerSigningFunctionRegistration);
      client.signalSender.setBeforeSendHook(client::refreshWorkerSigningFunctionRegistration);
      SigningIdentity identity = client.currentSigningIdentity();
      if (identity != null) {
        log.info(
            "Worker response signing configured from source={} (keyId={})",
            effectiveSigningIdentitySource.getSourceType(),
            identity.getKeyId());
      }
      if (effectiveAuthorizationTokenProvider != null) {
        log.info(
            "Client command authorization configured via provider={} for start/abort/set-variable/task-completion commands",
            effectiveAuthorizationTokenProvider.getClass().getSimpleName());
      }
      return client;
    }

    SecurityParticipantDescriptor resolveParticipantDescriptor(Properties properties) {
      if (participantDescriptor != null) {
        return validateClientParticipantDescriptor(
            properties, SecurityParticipantDescriptorSupport.requireValid(participantDescriptor));
      }

      TaktPropertiesHelper helper = new TaktPropertiesHelper(properties);
      Set<ParticipantCapability> inferredCapabilities = new LinkedHashSet<>();
      inferredCapabilities.add(ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT);
      inferredCapabilities.add(ParticipantCapability.SECURITY_OBSERVER);

      String inferredSigningKeyId =
          firstNonBlank(
              properties.getProperty("taktx.signing.key-id"),
              System.getProperty("taktx.signing.key-id"),
              System.getenv("TAKTX_SIGNING_KEY_ID"));
      String inferredComponentType =
          inferredSigningKeyId != null
                  && !inferredSigningKeyId.isBlank()
                  && properties.getProperty("taktx.client.component-type") == null
              ? inferredSigningKeyId.split("-", 2)[0]
              : firstNonBlank(
                  properties.getProperty("taktx.client.component-type"),
                  properties.getProperty("quarkus.application.name"),
                  properties.getProperty("spring.application.name"),
                  properties.getProperty("application.name"),
                  "generic-client");
      SecurityParticipantDescriptor inferredDescriptor =
          new SecurityParticipantDescriptor(
              firstNonBlank(
                  properties.getProperty("taktx.client.participant-id"),
                  properties.getProperty("taktx.participant.id"),
                  inferredSigningKeyId,
                  helper.getNamespace() + ".client"),
              ParticipantKind.CLIENT,
              inferredCapabilities,
              inferredComponentType);
      return validateClientParticipantDescriptor(
          properties, SecurityParticipantDescriptorSupport.requireValid(inferredDescriptor));
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
            "No worker signing identity configured via environment/system properties — falling back to managed local persistent identity");
        return new LocalPersistentSigningIdentitySource(properties, "client-");
      }
      if ("env".equalsIgnoreCase(sourceType) || "environment".equalsIgnoreCase(sourceType)) {
        return new EnvironmentWorkerSigningIdentitySource(properties, keyIdOverride);
      }
      if ("file".equalsIgnoreCase(sourceType)) {
        return new FileSigningIdentitySource(properties);
      }
      if ("local".equalsIgnoreCase(sourceType)) {
        return new LocalPersistentSigningIdentitySource(properties, "client-");
      }
      if ("generated".equalsIgnoreCase(sourceType)) {
        return new GeneratedSigningIdentitySource("client-");
      }
      throw new IllegalArgumentException(
          "Unsupported taktx.signing.identity-source='"
              + sourceType
              + "'. Supported values: env, file, local, generated");
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

    /**
     * Sets the result-processor factory used to adapt worker method return values.
     *
     * @param resultProcessorFactory factory used to create result processors for worker return
     *     types
     * @return this builder
     */
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

    /**
     * Declares the participant descriptor advertised by this client instance.
     *
     * @param participantDescriptor shared participant descriptor for this client instance
     * @return this builder
     */
    public TaktXClientBuilder withParticipantDescriptor(
        SecurityParticipantDescriptor participantDescriptor) {
      this.participantDescriptor = participantDescriptor;
      return this;
    }

    /**
     * Overrides the signing-identity source used by the client for worker response signing.
     *
     * @param signingIdentitySource signing-identity source to use
     * @return this builder
     */
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
     * @return this builder
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

    private SecurityParticipantDescriptor validateClientParticipantDescriptor(
        Properties properties, SecurityParticipantDescriptor descriptor) {
      if (descriptor.kind() != ParticipantKind.CLIENT) {
        throw new IllegalArgumentException(
            "TaktXClient participant descriptor kind must be CLIENT");
      }
      if (descriptor.capabilities().contains(ParticipantCapability.ENFORCER)) {
        throw new IllegalArgumentException(
            "TaktXClient participant descriptor must not declare ENFORCER");
      }
      return descriptor;
    }

    /**
     * Overrides the authorization-token provider used for entry commands.
     *
     * @param authorizationTokenProvider provider used to obtain outbound command JWTs
     * @return this builder
     */
    public TaktXClientBuilder withAuthorizationTokenProvider(
        AuthorizationTokenProvider authorizationTokenProvider) {
      this.authorizationTokenProvider = authorizationTokenProvider;
      return this;
    }
  }
}
