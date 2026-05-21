/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.topicmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.CleanupPolicy;
import io.taktx.dto.Constants;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.TopicMetaDlqEntryDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.dlq.DlqHeaders;
import io.taktx.engine.generic.TopologyProducer;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.security.AuthorizationTokenException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.Test;

class TopicMetaRequestIngressProcessorTest {

  private static final String STORE_NAME = "topic-meta-request-dedup-test-store";
  private static final String INPUT_TOPIC = "topic-meta-requested-input";
  private static final String OUTPUT_TOPIC = "topic-meta-requested-dlq-output";
  private static final String LOCAL_PREFIX = "acme.prod.";
  private static final String REQUESTED_TOPIC = LOCAL_PREFIX + "topic-meta-requested";

  @Test
  void authorizedValidRequest_isHandedOffToDynamicTopicManager() {
    EngineAuthorizationService authz = mock(EngineAuthorizationService.class);
    DynamicTopicManager topicManager = mock(DynamicTopicManager.class);
    when(authz.authorizeTopicMetaRequest(any(), any()))
        .thenReturn(activeKey("client-key-1", KeyRole.CLIENT));

    try (TestHarness harness = newHarness(authz, topicManager, 10_000L)) {
      TopicMetaDTO topicMeta = validTopicMeta("msg-1");

      harness.pipe(topicMeta.getTopicName(), topicMeta, signedHeaders("client-key-1.sig-a"));

      verify(topicManager).processRequestedTopic(topicMeta.getTopicName(), topicMeta);
      verify(topicManager, never()).publishRejectedRequestedTopic(any());
      assertThat(harness.outputTopic.isEmpty()).isTrue();
    }
  }

  @Test
  void unauthorizedRequest_publishesNullContractAndForwardsDlqEntry() {
    EngineAuthorizationService authz = mock(EngineAuthorizationService.class);
    DynamicTopicManager topicManager = mock(DynamicTopicManager.class);
    when(authz.authorizeTopicMetaRequest(any(), any()))
        .thenThrow(
            new AuthorizationTokenException(
                "Unknown Ed25519 keyId 'client-key-1' — signer not found in taktx-signing-keys KTable"));

    try (TestHarness harness = newHarness(authz, topicManager, 10_000L)) {
      TopicMetaDTO topicMeta = validTopicMeta("msg-2");

      harness.pipe(topicMeta.getTopicName(), topicMeta, signedHeaders("client-key-1.sig-a"));

      verify(topicManager).publishRejectedRequestedTopic(topicMeta.getTopicName());
      verify(topicManager, never()).processRequestedTopic(any(), any());
      assertThat(harness.outputTopic.getQueueSize()).isEqualTo(1);
      TopicMetaDlqEntryDTO dlqEntry = harness.outputTopic.readValue();
      assertThat(dlqEntry.getTopicName()).isEqualTo(topicMeta.getTopicName());
      assertThat(
              new String(dlqEntry.getHeaders().get(DlqHeaders.REASON_HINT), StandardCharsets.UTF_8))
          .isEqualTo(DlqReasonCode.SIGNATURE_KEY_UNKNOWN.name());
      assertThat(dlqEntry.getData()).isNotEmpty();
    }
  }

  @Test
  void validationFailure_preservesNullPublicationContractWithoutDlqForward() {
    EngineAuthorizationService authz = mock(EngineAuthorizationService.class);
    DynamicTopicManager topicManager = mock(DynamicTopicManager.class);
    when(authz.authorizeTopicMetaRequest(any(), any()))
        .thenReturn(activeKey("client-key-1", KeyRole.CLIENT));

    try (TestHarness harness = newHarness(authz, topicManager, 10_000L)) {
      TopicMetaDTO invalidTopicMeta =
          new TopicMetaDTO(
              LOCAL_PREFIX + "process-instance", 3, CleanupPolicy.DELETE, (short) 1, "msg-3");

      harness.pipe(
          invalidTopicMeta.getTopicName(), invalidTopicMeta, signedHeaders("client-key-1.sig-a"));

      verify(topicManager).publishRejectedRequestedTopic(invalidTopicMeta.getTopicName());
      verify(topicManager, never()).processRequestedTopic(any(), any());
      assertThat(harness.outputTopic.isEmpty()).isTrue();
    }
  }

  @Test
  void duplicateTopicMetaRequestWithinWindow_isSuppressed() {
    EngineAuthorizationService authz = mock(EngineAuthorizationService.class);
    DynamicTopicManager topicManager = mock(DynamicTopicManager.class);
    when(authz.authorizeTopicMetaRequest(any(), any()))
        .thenReturn(activeKey("client-key-1", KeyRole.CLIENT));

    try (TestHarness harness = newHarness(authz, topicManager, 10_000L)) {
      TopicMetaDTO topicMeta = validTopicMeta("msg-4");
      RecordHeaders headers = signedHeaders("client-key-1.sig-a");

      harness.pipe(topicMeta.getTopicName(), topicMeta, headers);
      harness.pipe(topicMeta.getTopicName(), topicMeta, headers);

      verify(topicManager, times(1)).processRequestedTopic(topicMeta.getTopicName(), topicMeta);
      assertThat(harness.outputTopic.isEmpty()).isTrue();
    }
  }

  @Test
  void duplicateTopicMetaRequestAfterExpiry_isAcceptedAgain() {
    EngineAuthorizationService authz = mock(EngineAuthorizationService.class);
    DynamicTopicManager topicManager = mock(DynamicTopicManager.class);
    when(authz.authorizeTopicMetaRequest(any(), any()))
        .thenReturn(activeKey("client-key-1", KeyRole.CLIENT));

    try (TestHarness harness = newHarness(authz, topicManager, 1_000L)) {
      TopicMetaDTO topicMeta = validTopicMeta("msg-5");
      RecordHeaders headers = signedHeaders("client-key-1.sig-a");

      harness.pipe(topicMeta.getTopicName(), topicMeta, headers);
      harness.advanceMillis(1_500L);
      harness.pipe(topicMeta.getTopicName(), topicMeta, headers);

      verify(topicManager, times(2)).processRequestedTopic(topicMeta.getTopicName(), topicMeta);
      assertThat(harness.outputTopic.isEmpty()).isTrue();
    }
  }

  @Test
  void missingMessageId_fallsBackToSignatureAndPayloadHash() {
    EngineAuthorizationService authz = mock(EngineAuthorizationService.class);
    DynamicTopicManager topicManager = mock(DynamicTopicManager.class);
    when(authz.authorizeTopicMetaRequest(any(), any()))
        .thenReturn(activeKey("client-key-1", KeyRole.CLIENT));

    try (TestHarness harness = newHarness(authz, topicManager, 10_000L)) {
      String topicName =
          LOCAL_PREFIX + Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker";
      TopicMetaDTO topicMeta =
          new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1, null);
      RecordHeaders headers = signedHeaders("client-key-1.sig-a");

      harness.pipe(topicName, topicMeta, headers);
      harness.pipe(topicName, topicMeta, headers);

      verify(topicManager, times(1)).processRequestedTopic(topicName, topicMeta);
    }
  }

  @Test
  void reasonCodeForAuthorizationFailure_mapsKnownVerificationMessages() {
    assertThat(
            TopicMetaRequestIngressProcessor.reasonCodeForAuthorizationFailure(
                new AuthorizationTokenException(
                    "Missing required tx-sig header — required role: CLIENT")))
        .isEqualTo(DlqReasonCode.SIGNATURE_MISSING);
    assertThat(
            TopicMetaRequestIngressProcessor.reasonCodeForAuthorizationFailure(
                new AuthorizationTokenException(
                    "Unknown Ed25519 keyId 'client-key-1' — signer not found in taktx-signing-keys KTable")))
        .isEqualTo(DlqReasonCode.SIGNATURE_KEY_UNKNOWN);
    assertThat(
            TopicMetaRequestIngressProcessor.reasonCodeForAuthorizationFailure(
                new AuthorizationTokenException(
                    "Revoked Ed25519 keyId 'client-key-1' — rejecting message")))
        .isEqualTo(DlqReasonCode.SIGNATURE_KEY_REVOKED);
    assertThat(
            TopicMetaRequestIngressProcessor.reasonCodeForAuthorizationFailure(
                new AuthorizationTokenException(
                    "Signing keyId 'client-key-1' (role=CLIENT) is not trusted for required role CLIENT")))
        .isEqualTo(DlqReasonCode.AUTHORIZATION_FAILED);
  }

  private static TestHarness newHarness(
      EngineAuthorizationService authz, DynamicTopicManager topicManager, long retentionMs) {
    TaktConfiguration taktConfiguration = mock(TaktConfiguration.class);
    when(taktConfiguration.getPrefixed(any()))
        .thenAnswer(invocation -> LOCAL_PREFIX + invocation.getArgument(0));

    RequestedTopicValidator requestedTopicValidator =
        new RequestedTopicValidator(taktConfiguration);
    AtomicLong nowMs = new AtomicLong(Instant.parse("2026-05-15T10:00:00Z").toEpochMilli());
    Clock clock = new TestClock(nowMs);

    StreamsBuilder builder = new StreamsBuilder();
    builder.addStateStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(STORE_NAME), Serdes.String(), Serdes.Long())
            .withLoggingDisabled());

    builder.stream(
            INPUT_TOPIC,
            Consumed.with(TopologyProducer.TOPIC_META_KEY_SERDE, TopologyProducer.TOPIC_META_SERDE))
        .process(
            () ->
                new TopicMetaRequestIngressProcessor(
                    clock,
                    retentionMs,
                    STORE_NAME,
                    REQUESTED_TOPIC,
                    authz,
                    requestedTopicValidator,
                    topicManager),
            STORE_NAME)
        .to(
            OUTPUT_TOPIC,
            Produced.with(
                TopologyProducer.TOPIC_META_KEY_SERDE,
                TopologyProducer.TOPIC_META_DLQ_ENTRY_SERDE));

    Properties props = new Properties();
    props.put(
        StreamsConfig.APPLICATION_ID_CONFIG,
        "topic-meta-request-ingress-test-" + retentionMs + "-" + UUID.randomUUID());
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");

    TopologyTestDriver driver = new TopologyTestDriver(builder.build(), props);
    TestInputTopic<String, TopicMetaDTO> inputTopic =
        driver.createInputTopic(
            INPUT_TOPIC,
            TopologyProducer.TOPIC_META_KEY_SERDE.serializer(),
            TopologyProducer.TOPIC_META_SERDE.serializer());
    TestOutputTopic<String, TopicMetaDlqEntryDTO> outputTopic =
        driver.createOutputTopic(
            OUTPUT_TOPIC,
            TopologyProducer.TOPIC_META_KEY_SERDE.deserializer(),
            TopologyProducer.TOPIC_META_DLQ_ENTRY_SERDE.deserializer());
    return new TestHarness(driver, inputTopic, outputTopic, nowMs);
  }

  private static TopicMetaDTO validTopicMeta(String messageId) {
    String topicName =
        LOCAL_PREFIX + Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + "payment-worker";
    return new TopicMetaDTO(topicName, 3, CleanupPolicy.DELETE, (short) 1, messageId);
  }

  private static RecordHeaders signedHeaders(String signatureValue) {
    RecordHeaders headers = new RecordHeaders();
    headers.add(Constants.HEADER_ENGINE_SIGNATURE, signatureValue.getBytes(StandardCharsets.UTF_8));
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

  private record TestHarness(
      TopologyTestDriver driver,
      TestInputTopic<String, TopicMetaDTO> inputTopic,
      TestOutputTopic<String, TopicMetaDlqEntryDTO> outputTopic,
      AtomicLong nowMs)
      implements AutoCloseable {

    private void pipe(String key, TopicMetaDTO value, RecordHeaders headers) {
      inputTopic.pipeInput(
          new TestRecord<>(key, value, headers, Instant.ofEpochMilli(nowMs.get())));
    }

    private void advanceMillis(long millis) {
      nowMs.addAndGet(millis);
      driver.advanceWallClockTime(Duration.ofMillis(millis));
    }

    @Override
    public void close() {
      driver.close();
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
