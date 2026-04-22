/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Jwts;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.taktx.dto.Constants;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ReplayProtectionMode;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.TopologyProducer;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
import io.taktx.engine.pi.integration.SecurityTestConfigResource;
import io.taktx.engine.pi.integration.SecurityTestProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(SecurityTestProfile.class)
@QuarkusTestResource(value = SecurityTestConfigResource.class, restrictToAnnotatedClass = true)
class ReplayProtectionRestorationIntegrationTest {

  private static final String TENANT = "test-tenant";
  private static final String NAMESPACE = "default";
  private static final String PLATFORM_KID = "platform-replay-restoration-key";
  private static final String ISSUER = "taktx-platform-restoration";
  private static final String REPLAY_STORE_NAME = TENANT + "." + NAMESPACE + ".replay-protection";

  @Test
  void replayStateRestoresAfterRestartWithFreshLocalState() throws Exception {
    String bootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    String uniqueSuffix = UUID.randomUUID().toString();
    String inputTopic = TENANT + "." + NAMESPACE + ".replay-restoration-input-" + uniqueSuffix;
    String outputTopic = TENANT + "." + NAMESPACE + ".replay-restoration-output-" + uniqueSuffix;
    String applicationId = TENANT + "." + NAMESPACE + ".replay-restoration-" + uniqueSuffix;
    String auditId = "audit-" + uniqueSuffix;
    String replayKey = TENANT + ":" + NAMESPACE + ":" + ISSUER + ":" + auditId;
    KeyPair rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    Path firstStateDir = Files.createTempDirectory("replay-restoration-first-");
    Path secondStateDir = Files.createTempDirectory("replay-restoration-second-");

    createTopics(bootstrapServers, inputTopic, outputTopic);

    try (KafkaProducer<String, ProcessInstanceTriggerEnvelope> producer = createProducer(bootstrapServers);
        KafkaConsumer<UUID, ProcessInstanceTriggerEnvelope> outputConsumer =
            createConsumer(bootstrapServers, outputTopic)) {
      awaitConsumerAssignment(outputConsumer);

      UUID firstProcessInstanceId = UUID.randomUUID();
      String jwt = buildJwt(rsaKeyPair, auditId);

      try (ManagedStreams firstRun =
          startStreams(
              bootstrapServers,
              applicationId,
              firstStateDir,
              inputTopic,
              outputTopic,
              createAuthorizationService(rsaKeyPair))) {
        producer.send(replayProtectedRecord(inputTopic, firstProcessInstanceId, jwt, replayKey)).get();

        ConsumerRecord<UUID, ProcessInstanceTriggerEnvelope> forwardedRecord =
            awaitForwardedRecord(outputConsumer);
        assertThat(forwardedRecord.key()).isEqualTo(firstProcessInstanceId);
        assertReplayStatePresent(firstRun.streams(), replayKey);
      }

      awaitConsumerGroupInactive(bootstrapServers, applicationId);

      UUID secondProcessInstanceId = UUID.randomUUID();
      try (ManagedStreams secondRun =
          startStreams(
              bootstrapServers,
              applicationId,
              secondStateDir,
              inputTopic,
              outputTopic,
              createAuthorizationService(rsaKeyPair))) {
        assertReplayStatePresent(secondRun.streams(), replayKey);

        producer.send(replayProtectedRecord(inputTopic, secondProcessInstanceId, jwt, replayKey)).get();

        assertNoForwardedRecords(outputConsumer);
      }
    } finally {
      deleteRecursively(firstStateDir);
      deleteRecursively(secondStateDir);
    }
  }

  private static ManagedStreams startStreams(
      String bootstrapServers,
      String applicationId,
      Path stateDir,
      String inputTopic,
      String outputTopic,
      EngineAuthorizationService authorizationService) {
    Properties properties = new Properties();
    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDir.toString());
    properties.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 1);
    properties.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);
    properties.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
    properties.put(StreamsConfig.consumerPrefix(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG), 6_000);
    properties.put(StreamsConfig.consumerPrefix(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG), 2_000);
    properties.put(
        StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
        org.apache.kafka.common.serialization.Serdes.StringSerde.class);
    properties.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
        org.apache.kafka.common.serialization.Serdes.ByteArraySerde.class);

    KafkaStreams streams =
        new KafkaStreams(buildTopology(inputTopic, outputTopic, authorizationService), properties);
    streams.start();

    await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> streams.state() == KafkaStreams.State.RUNNING);

    return new ManagedStreams(streams);
  }

  private static Topology buildTopology(
      String inputTopic, String outputTopic, EngineAuthorizationService authorizationService) {
    StreamsBuilder builder = new StreamsBuilder();
    builder.addStateStore(
        org.apache.kafka.streams.state.Stores.keyValueStoreBuilder(
            org.apache.kafka.streams.state.Stores.persistentKeyValueStore(REPLAY_STORE_NAME),
            Serdes.String(),
            Serdes.Long()));

    builder.stream(
            inputTopic,
            Consumed.with(
                Serdes.String(), TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE))
        .process(
            () ->
                new ReplayProtectionProcessor(
                    java.time.Clock.systemUTC(), authorizationService, REPLAY_STORE_NAME),
            REPLAY_STORE_NAME)
        .to(
            outputTopic,
            Produced.with(
                TopologyProducer.PROCESS_INSTANCE_KEY_SERDE,
                TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE));

    return builder.build();
  }

  private static EngineAuthorizationService createAuthorizationService(KeyPair rsaKeyPair) {
    TaktConfiguration configuration = mock(TaktConfiguration.class);
    when(configuration.getTenantId()).thenReturn(TENANT);
    when(configuration.getNamespace()).thenReturn(NAMESPACE);

    GlobalConfigStore globalConfigStore = new GlobalConfigStore();
    globalConfigStore.update(
        GlobalConfigurationDTO.builder()
            .engineRequiresAuthorization(true)
            .replayProtectionMode(ReplayProtectionMode.COMPAT)
            .replayProtectionRetentionMs(600_000L)
            .build());

    PublicKeyProvider publicKeyProvider = mock(PublicKeyProvider.class);
    when(publicKeyProvider.getKey(PLATFORM_KID)).thenReturn(rsaKeyPair.getPublic());

    return new EngineAuthorizationService(
        configuration,
        globalConfigStore,
        publicKeyProvider,
        new NonceStore(),
        mock(org.apache.kafka.streams.KafkaStreams.class));
  }

  private static ProducerRecord<String, ProcessInstanceTriggerEnvelope> replayProtectedRecord(
      String inputTopic, UUID processInstanceId, String jwt, String replayKey) {
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(startCommand(processInstanceId), false, null)
            .withReplayRoutingKeyHint(replayKey);
    ProducerRecord<String, ProcessInstanceTriggerEnvelope> producedRecord =
        new ProducerRecord<>(inputTopic, replayKey, envelope);
    producedRecord
        .headers()
        .add(Constants.HEADER_AUTHORIZATION, jwt.getBytes(StandardCharsets.UTF_8));
    return producedRecord;
  }

  private static StartCommandDTO startCommand(UUID processInstanceId) {
    return new StartCommandDTO(
        processInstanceId,
        null,
        null,
        new ProcessDefinitionKey("proc", -1),
        VariablesDTO.empty());
  }

  private static String buildJwt(KeyPair rsaKeyPair, String auditId) {
    return Jwts.builder()
        .header()
        .keyId(PLATFORM_KID)
        .and()
        .subject("user-restoration")
        .issuer(ISSUER)
        .claim("action", "START")
        .claim("processDefinitionId", "proc")
        .claim("version", -1)
        .claim("namespaceId", UUID.randomUUID().toString())
        .claim("auditId", auditId)
        .expiration(Date.from(Instant.now().plusSeconds(300)))
        .signWith(rsaKeyPair.getPrivate())
        .compact();
  }

  private static KafkaProducer<String, ProcessInstanceTriggerEnvelope> createProducer(
      String bootstrapServers) {
    Properties properties = new Properties();
    properties.put("bootstrap.servers", bootstrapServers);
    properties.put("acks", "all");
    return new KafkaProducer<>(
        properties,
        Serdes.String().serializer(),
        TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE.serializer());
  }

  private static KafkaConsumer<UUID, ProcessInstanceTriggerEnvelope> createConsumer(
      String bootstrapServers, String topic) {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "replay-restoration-consumer-" + UUID.randomUUID());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    KafkaConsumer<UUID, ProcessInstanceTriggerEnvelope> consumer =
        new KafkaConsumer<>(
            properties,
            TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer(),
            TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE.deserializer());
    consumer.subscribe(List.of(topic));
    return consumer;
  }

  private static void awaitConsumerAssignment(KafkaConsumer<?, ?> consumer) {
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              consumer.poll(Duration.ofMillis(100));
              return !consumer.assignment().isEmpty();
            });
  }

  private static ConsumerRecord<UUID, ProcessInstanceTriggerEnvelope> awaitForwardedRecord(
      KafkaConsumer<UUID, ProcessInstanceTriggerEnvelope> consumer) {
    return await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              ConsumerRecords<UUID, ProcessInstanceTriggerEnvelope> records =
                  consumer.poll(Duration.ofMillis(200));
              return records.isEmpty() ? null : records.iterator().next();
            },
            java.util.Objects::nonNull);
  }

  private static void assertNoForwardedRecords(
      KafkaConsumer<UUID, ProcessInstanceTriggerEnvelope> consumer) {
    ConsumerRecords<UUID, ProcessInstanceTriggerEnvelope> records =
        consumer.poll(Duration.ofSeconds(3));
    assertThat(records.count()).isZero();
  }

  private static void assertReplayStatePresent(KafkaStreams streams, String replayKey) {
    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              ReadOnlyKeyValueStore<String, Long> store =
                  streams.store(
                      org.apache.kafka.streams.StoreQueryParameters.fromNameAndType(
                          REPLAY_STORE_NAME, QueryableStoreTypes.keyValueStore()));
              assertThat(store.get(replayKey)).isNotNull();
            });
  }

  private static void createTopics(String bootstrapServers, String... topicNames) throws Exception {
    Properties properties = new Properties();
    properties.put("bootstrap.servers", bootstrapServers);
    try (AdminClient adminClient = AdminClient.create(properties)) {
      adminClient
          .createTopics(
              java.util.Arrays.stream(topicNames)
                  .map(topic -> new NewTopic(topic, 1, (short) 1))
                  .toList())
          .all()
          .get();
    }
  }

  private static void awaitConsumerGroupInactive(String bootstrapServers, String consumerGroupId) {
    Properties properties = new Properties();
    properties.put("bootstrap.servers", bootstrapServers);

    try (AdminClient adminClient = AdminClient.create(properties)) {
      await()
          .atMost(Duration.ofSeconds(20))
          .pollInterval(Duration.ofMillis(200))
          .until(
              () -> isConsumerGroupInactive(adminClient, consumerGroupId));
    }
  }

  private static boolean isConsumerGroupInactive(AdminClient adminClient, String consumerGroupId)
      throws Exception {
    ConsumerGroupDescription description =
        adminClient.describeConsumerGroups(List.of(consumerGroupId)).describedGroups().get(consumerGroupId).get();
    String groupState = description.groupState().toString();

    return description.members().isEmpty()
        || "EMPTY".equals(groupState)
        || "DEAD".equals(groupState);
  }

  private static void deleteRecursively(Path directory) throws IOException {
    if (directory == null || Files.notExists(directory)) {
      return;
    }
    try (var paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(ReplayProtectionRestorationIntegrationTest::deleteQuietly);
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to delete test path " + path, e);
    }
  }

  private record ManagedStreams(KafkaStreams streams) implements AutoCloseable {
    @Override
    public void close() {
      streams.close(Duration.ofSeconds(20));
      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .until(
              () -> {
                KafkaStreams.State state = streams.state();
                return state == KafkaStreams.State.NOT_RUNNING || state == KafkaStreams.State.ERROR;
              });
    }
  }
}







