/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.topicmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import io.taktx.CleanupPolicy;
import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.TopicMetaDlqEntryDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.TopologyProducer;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.engine.security.ProcessInstanceResponseDedupProcessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("security-integration")
class PhaseOneDedupIntegrationTest {

  private static final String TENANT = "test-tenant";
  private static final String NAMESPACE = "default";
  private static final String LOCAL_PREFIX = TENANT + "." + NAMESPACE + ".";
  private static final ObjectMapperSerde<TopicMetaDlqEntryDTO> TOPIC_META_DLQ_ENTRY_SERDE =
      new ObjectMapperSerde<>(TopicMetaDlqEntryDTO.class);

  @Container
  private static final ConfluentKafkaContainer KAFKA =
      new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @Test
  void externalTaskResponse_firstPasses_duplicateRejected_afterExpiryAccepted() throws Exception {
    String bootstrapServers = KAFKA.getBootstrapServers();
    String uniqueSuffix = UUID.randomUUID().toString();
    String inputTopic = LOCAL_PREFIX + "phase1-ext-response-input-" + uniqueSuffix;
    String outputTopic = LOCAL_PREFIX + "phase1-ext-response-output-" + uniqueSuffix;
    String applicationId = LOCAL_PREFIX + "phase1-ext-response-" + uniqueSuffix;
    String storeName = LOCAL_PREFIX + "phase1-ext-response-store-" + uniqueSuffix;
    Path stateDir = Files.createTempDirectory("phase1-ext-response-");
    AtomicLong nowMs = new AtomicLong(Instant.parse("2026-05-15T12:00:00Z").toEpochMilli());

    createTopics(bootstrapServers, inputTopic, outputTopic);

    try (KafkaProducer<UUID, ProcessInstanceTriggerEnvelope> producer =
            createProcessInstanceProducer(bootstrapServers);
        KafkaConsumer<UUID, ProcessInstanceTriggerEnvelope> consumer =
            createProcessInstanceConsumer(bootstrapServers, outputTopic);
        var _ =
            startStreams(
                applicationId,
                stateDir,
                bootstrapServers,
                buildProcessInstanceResponseTopology(
                    inputTopic, outputTopic, storeName, new TestClock(nowMs), 1_000L))) {
      awaitConsumerAssignment(consumer);

      UUID processInstanceId = UUID.randomUUID();
      ExternalTaskResponseTriggerDTO trigger =
          new ExternalTaskResponseTriggerDTO(
              processInstanceId,
              java.util.List.of(10L, 20L),
              "msg-ext-it-1",
              new ExternalTaskResponseResultDTO(
                  ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
              VariablesDTO.empty());

      producer
          .send(
              processInstanceResponseRecord(
                  inputTopic, processInstanceId, trigger, signedHeaders()))
          .get();
      ConsumerRecord<UUID, ProcessInstanceTriggerEnvelope> first =
          awaitForwardedProcessInstanceRecord(consumer);
      assertThat(first.key()).isEqualTo(processInstanceId);
      assertThat(first.value().trigger()).isInstanceOf(ExternalTaskResponseTriggerDTO.class);

      producer
          .send(
              processInstanceResponseRecord(
                  inputTopic, processInstanceId, trigger, signedHeaders()))
          .get();
      assertNoForwardedProcessInstanceRecords(consumer);

      nowMs.addAndGet(1_500L);
      producer
          .send(
              processInstanceResponseRecord(
                  inputTopic, processInstanceId, trigger, signedHeaders()))
          .get();
      ConsumerRecord<UUID, ProcessInstanceTriggerEnvelope> afterExpiry =
          awaitForwardedProcessInstanceRecord(consumer);
      assertThat(afterExpiry.key()).isEqualTo(processInstanceId);
      assertThat(afterExpiry.value().trigger()).isInstanceOf(ExternalTaskResponseTriggerDTO.class);
    } finally {
      deleteRecursively(stateDir);
    }
  }

  @Test
  void userTaskResponse_firstPasses_duplicateRejected_afterExpiryAccepted() throws Exception {
    String bootstrapServers = KAFKA.getBootstrapServers();
    String uniqueSuffix = UUID.randomUUID().toString();
    String inputTopic = LOCAL_PREFIX + "phase1-user-response-input-" + uniqueSuffix;
    String outputTopic = LOCAL_PREFIX + "phase1-user-response-output-" + uniqueSuffix;
    String applicationId = LOCAL_PREFIX + "phase1-user-response-" + uniqueSuffix;
    String storeName = LOCAL_PREFIX + "phase1-user-response-store-" + uniqueSuffix;
    Path stateDir = Files.createTempDirectory("phase1-user-response-");
    AtomicLong nowMs = new AtomicLong(Instant.parse("2026-05-15T12:10:00Z").toEpochMilli());

    createTopics(bootstrapServers, inputTopic, outputTopic);

    try (KafkaProducer<UUID, ProcessInstanceTriggerEnvelope> producer =
            createProcessInstanceProducer(bootstrapServers);
        KafkaConsumer<UUID, ProcessInstanceTriggerEnvelope> consumer =
            createProcessInstanceConsumer(bootstrapServers, outputTopic);
        var _ =
            startStreams(
                applicationId,
                stateDir,
                bootstrapServers,
                buildProcessInstanceResponseTopology(
                    inputTopic, outputTopic, storeName, new TestClock(nowMs), 1_000L))) {
      awaitConsumerAssignment(consumer);

      UUID processInstanceId = UUID.randomUUID();
      UserTaskResponseTriggerDTO trigger =
          new UserTaskResponseTriggerDTO(
              processInstanceId,
              java.util.List.of(30L, 40L),
              "msg-user-it-1",
              new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null),
              VariablesDTO.empty());

      producer
          .send(
              processInstanceResponseRecord(
                  inputTopic, processInstanceId, trigger, signedHeaders()))
          .get();
      ConsumerRecord<UUID, ProcessInstanceTriggerEnvelope> first =
          awaitForwardedProcessInstanceRecord(consumer);
      assertThat(first.key()).isEqualTo(processInstanceId);
      assertThat(first.value().trigger()).isInstanceOf(UserTaskResponseTriggerDTO.class);

      producer
          .send(
              processInstanceResponseRecord(
                  inputTopic, processInstanceId, trigger, signedHeaders()))
          .get();
      assertNoForwardedProcessInstanceRecords(consumer);

      nowMs.addAndGet(1_500L);
      producer
          .send(
              processInstanceResponseRecord(
                  inputTopic, processInstanceId, trigger, signedHeaders()))
          .get();
      ConsumerRecord<UUID, ProcessInstanceTriggerEnvelope> afterExpiry =
          awaitForwardedProcessInstanceRecord(consumer);
      assertThat(afterExpiry.key()).isEqualTo(processInstanceId);
      assertThat(afterExpiry.value().trigger()).isInstanceOf(UserTaskResponseTriggerDTO.class);
    } finally {
      deleteRecursively(stateDir);
    }
  }

  @Test
  void topicMetaRequest_firstPasses_duplicateRejected_afterExpiryAccepted() throws Exception {
    String bootstrapServers = KAFKA.getBootstrapServers();
    String uniqueSuffix = UUID.randomUUID().toString();
    String inputTopic = LOCAL_PREFIX + "phase1-topic-meta-input-" + uniqueSuffix;
    String outputTopic = LOCAL_PREFIX + "phase1-topic-meta-output-" + uniqueSuffix;
    String applicationId = LOCAL_PREFIX + "phase1-topic-meta-" + uniqueSuffix;
    String storeName = LOCAL_PREFIX + "phase1-topic-meta-store-" + uniqueSuffix;
    Path stateDir = Files.createTempDirectory("phase1-topic-meta-");
    AtomicLong nowMs = new AtomicLong(Instant.parse("2026-05-15T12:20:00Z").toEpochMilli());

    createTopics(bootstrapServers, inputTopic, outputTopic);

    EngineAuthorizationService authz = mock(EngineAuthorizationService.class);
    DynamicTopicManager topicManager = mock(DynamicTopicManager.class);
    when(authz.authorizeTopicMetaRequest(any(), any()))
        .thenReturn(activeKey("client-key-1", KeyRole.CLIENT));

    TaktConfiguration configuration = mock(TaktConfiguration.class);
    when(configuration.getPrefixed(any()))
        .thenAnswer(invocation -> LOCAL_PREFIX + invocation.getArgument(0, String.class));
    RequestedTopicValidator validator = new RequestedTopicValidator(configuration);

    String requestedTopicName = LOCAL_PREFIX + "topic-meta-requested";
    String topicName =
        LOCAL_PREFIX + Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker";
    TopicMetaDTO topicMeta =
        new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1, "msg-topic-meta-it-1");

    try (KafkaProducer<String, TopicMetaDTO> producer = createTopicMetaProducer(bootstrapServers);
        KafkaConsumer<String, TopicMetaDlqEntryDTO> consumer =
            createTopicMetaDlqConsumer(bootstrapServers, outputTopic);
        var _ =
            startStreams(
                applicationId,
                stateDir,
                bootstrapServers,
                buildTopicMetaTopology(
                    inputTopic,
                    outputTopic,
                    storeName,
                    requestedTopicName,
                    new TestClock(nowMs),
                    1_000L,
                    authz,
                    validator,
                    topicManager))) {
      awaitConsumerAssignment(consumer);

      producer.send(topicMetaRecord(inputTopic, topicName, topicMeta, signedHeaders())).get();
      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(
              () -> verify(topicManager, times(1)).processRequestedTopic(topicName, topicMeta));
      assertNoTopicMetaDlqRecords(consumer);

      producer.send(topicMetaRecord(inputTopic, topicName, topicMeta, signedHeaders())).get();
      await()
          .during(Duration.ofSeconds(2))
          .atMost(Duration.ofSeconds(3))
          .untilAsserted(
              () -> verify(topicManager, times(1)).processRequestedTopic(topicName, topicMeta));
      verify(topicManager, never()).publishRejectedRequestedTopic(any());
      assertNoTopicMetaDlqRecords(consumer);

      nowMs.addAndGet(1_500L);
      producer.send(topicMetaRecord(inputTopic, topicName, topicMeta, signedHeaders())).get();
      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(
              () -> verify(topicManager, times(2)).processRequestedTopic(topicName, topicMeta));
      assertNoTopicMetaDlqRecords(consumer);
    } finally {
      deleteRecursively(stateDir);
    }
  }

  private static Topology buildProcessInstanceResponseTopology(
      String inputTopic, String outputTopic, String storeName, Clock clock, long retentionMs) {
    StreamsBuilder builder = new StreamsBuilder();
    builder.addStateStore(
        Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(storeName),
            org.apache.kafka.common.serialization.Serdes.String(),
            org.apache.kafka.common.serialization.Serdes.Long()));

    builder.stream(
            inputTopic,
            Consumed.with(
                TopologyProducer.PROCESS_INSTANCE_KEY_SERDE,
                TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE))
        .process(
            () -> new ProcessInstanceResponseDedupProcessor(clock, retentionMs, storeName),
            storeName)
        .to(
            outputTopic,
            Produced.with(
                TopologyProducer.PROCESS_INSTANCE_KEY_SERDE,
                TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE));

    return builder.build();
  }

  private static Topology buildTopicMetaTopology(
      String inputTopic,
      String outputTopic,
      String storeName,
      String requestedTopicName,
      Clock clock,
      long retentionMs,
      EngineAuthorizationService authz,
      RequestedTopicValidator validator,
      DynamicTopicManager topicManager) {
    StreamsBuilder builder = new StreamsBuilder();
    builder.addStateStore(
        Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(storeName),
            org.apache.kafka.common.serialization.Serdes.String(),
            org.apache.kafka.common.serialization.Serdes.Long()));

    builder.stream(
            inputTopic,
            Consumed.with(TopologyProducer.TOPIC_META_KEY_SERDE, TopologyProducer.TOPIC_META_SERDE))
        .process(
            () ->
                new TopicMetaRequestIngressProcessor(
                    clock,
                    retentionMs,
                    storeName,
                    requestedTopicName,
                    authz,
                    validator,
                    topicManager),
            storeName)
        .to(
            outputTopic,
            Produced.with(TopologyProducer.TOPIC_META_KEY_SERDE, TOPIC_META_DLQ_ENTRY_SERDE));

    return builder.build();
  }

  private static ManagedStreams startStreams(
      String applicationId, Path stateDir, String bootstrapServers, Topology topology) {
    Properties properties = new Properties();
    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
    properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDir.toString());
    properties.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 1);
    properties.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);
    properties.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);

    KafkaStreams streams = new KafkaStreams(topology, properties);
    streams.start();
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> streams.state() == KafkaStreams.State.RUNNING);
    return new ManagedStreams(streams);
  }

  private static KafkaProducer<UUID, ProcessInstanceTriggerEnvelope> createProcessInstanceProducer(
      String bootstrapServers) {
    Properties properties = new Properties();
    properties.put("bootstrap.servers", bootstrapServers);
    properties.put("acks", "all");
    return new KafkaProducer<>(
        properties,
        TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.serializer(),
        TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE.serializer());
  }

  private static KafkaConsumer<UUID, ProcessInstanceTriggerEnvelope> createProcessInstanceConsumer(
      String bootstrapServers, String topic) {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "phase1-response-consumer-" + UUID.randomUUID());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    KafkaConsumer<UUID, ProcessInstanceTriggerEnvelope> consumer =
        new KafkaConsumer<>(
            properties,
            TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer(),
            TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE.deserializer());
    consumer.subscribe(java.util.List.of(topic));
    return consumer;
  }

  private static KafkaProducer<String, TopicMetaDTO> createTopicMetaProducer(
      String bootstrapServers) {
    Properties properties = new Properties();
    properties.put("bootstrap.servers", bootstrapServers);
    properties.put("acks", "all");
    return new KafkaProducer<>(
        properties,
        TopologyProducer.TOPIC_META_KEY_SERDE.serializer(),
        TopologyProducer.TOPIC_META_SERDE.serializer());
  }

  private static KafkaConsumer<String, TopicMetaDlqEntryDTO> createTopicMetaDlqConsumer(
      String bootstrapServers, String topic) {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(
        ConsumerConfig.GROUP_ID_CONFIG, "phase1-topic-meta-consumer-" + UUID.randomUUID());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    KafkaConsumer<String, TopicMetaDlqEntryDTO> consumer =
        new KafkaConsumer<>(
            properties, new StringDeserializer(), TOPIC_META_DLQ_ENTRY_SERDE.deserializer());
    consumer.subscribe(java.util.List.of(topic));
    return consumer;
  }

  private static ProducerRecord<UUID, ProcessInstanceTriggerEnvelope> processInstanceResponseRecord(
      String inputTopic,
      UUID processInstanceId,
      io.taktx.dto.ProcessInstanceTriggerDTO trigger,
      RecordHeaders headers) {
    ProcessInstanceTriggerEnvelope envelope =
        new ProcessInstanceTriggerEnvelope(new byte[0], trigger, false, null);
    ProducerRecord<UUID, ProcessInstanceTriggerEnvelope> piTriggerRecord =
        new ProducerRecord<>(inputTopic, processInstanceId, envelope);
    headers.forEach(header -> piTriggerRecord.headers().add(header));
    return piTriggerRecord;
  }

  private static ProducerRecord<String, TopicMetaDTO> topicMetaRecord(
      String inputTopic, String key, TopicMetaDTO value, RecordHeaders headers) {
    ProducerRecord<String, TopicMetaDTO> topicMetaRecord =
        new ProducerRecord<>(inputTopic, key, value);
    headers.forEach(header -> topicMetaRecord.headers().add(header));
    return topicMetaRecord;
  }

  private static RecordHeaders signedHeaders() {
    RecordHeaders headers = new RecordHeaders();
    headers.add(
        Constants.HEADER_ENGINE_SIGNATURE, "worker-key.sig-a".getBytes(StandardCharsets.UTF_8));
    return headers;
  }

  private static SigningKeyDTO activeKey(String keyId, KeyRole role) {
    return SigningKeyDTO.builder()
        .keyId(keyId)
        .publicKeyBase64("dummy")
        .algorithm("Ed25519")
        .owner("worker")
        .role(role)
        .build();
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

  private static ConsumerRecord<UUID, ProcessInstanceTriggerEnvelope>
      awaitForwardedProcessInstanceRecord(
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

  private static void assertNoForwardedProcessInstanceRecords(
      KafkaConsumer<UUID, ProcessInstanceTriggerEnvelope> consumer) {
    ConsumerRecords<UUID, ProcessInstanceTriggerEnvelope> records =
        consumer.poll(Duration.ofSeconds(3));
    assertThat(records.count()).isZero();
  }

  private static void assertNoTopicMetaDlqRecords(
      KafkaConsumer<String, TopicMetaDlqEntryDTO> consumer) {
    ConsumerRecords<String, TopicMetaDlqEntryDTO> records = consumer.poll(Duration.ofSeconds(2));
    assertThat(records.count()).isZero();
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

  private static void deleteRecursively(Path directory) throws IOException {
    if (directory == null || Files.notExists(directory)) {
      return;
    }
    try (var paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(PhaseOneDedupIntegrationTest::deleteQuietly);
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

  private static final class TestClock extends Clock {
    private final AtomicLong nowMs;

    private TestClock(AtomicLong nowMs) {
      this.nowMs = nowMs;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(nowMs.get());
    }
  }
}
