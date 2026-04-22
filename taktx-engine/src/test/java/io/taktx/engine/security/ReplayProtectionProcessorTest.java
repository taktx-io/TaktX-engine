/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Jwts;
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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.Test;

class ReplayProtectionProcessorTest {

  private static final String INPUT_TOPIC = "test-tenant.default.process-instance-input";
  private static final String OUTPUT_TOPIC = "test-tenant.default.process-instance-output";
  private static final String REPLAY_STORE_NAME = "test-tenant.default.replay-protection";
  private static final String PLATFORM_KID = "platform-key-replay-test";
  private static final String ISSUER = "taktx-platform";

  @Test
  void compatMode_rejectsDuplicateNonBlankAuditId() throws Exception {
    TestHarness harness = createHarness(ReplayProtectionMode.COMPAT, 600_000L, true);
    UUID processInstanceId = UUID.randomUUID();
    String jwt = harness.buildJwt("audit-compat-1");

    harness.pipe(processInstanceId, jwt);
    harness.pipe(processInstanceId, jwt);

    assertThat(harness.outputQueueSize()).isEqualTo(1);
  }

  @Test
  void offMode_allowsDuplicateAuditIds() throws Exception {
    TestHarness harness = createHarness(ReplayProtectionMode.OFF, 600_000L, true);
    UUID processInstanceId = UUID.randomUUID();
    String jwt = harness.buildJwt("audit-off-1");

    harness.pipe(processInstanceId, jwt);
    harness.pipe(processInstanceId, jwt);

    assertThat(harness.outputQueueSize()).isEqualTo(2);
  }

  @Test
  void strictMode_rejectsBlankAuditId() throws Exception {
    TestHarness harness = createHarness(ReplayProtectionMode.STRICT, 600_000L, true);

    harness.pipe(UUID.randomUUID(), harness.buildJwt("   "));

    assertThat(harness.outputQueueSize()).isZero();
  }

  @Test
  void compatMode_allowsBlankAuditId() throws Exception {
    TestHarness harness = createHarness(ReplayProtectionMode.COMPAT, 600_000L, true);

    harness.pipe(UUID.randomUUID(), harness.buildJwt("   "));

    assertThat(harness.outputQueueSize()).isEqualTo(1);
  }

  @Test
  void retentionExpiry_allowsAuditIdAgainAfterWindowPasses() throws Exception {
    TestHarness harness = createHarness(ReplayProtectionMode.COMPAT, 1_000L, true);
    UUID processInstanceId = UUID.randomUUID();
    String jwt = harness.buildJwt("audit-retention-1");

    harness.pipe(processInstanceId, jwt);
    harness.advanceMillis();
    harness.pipe(processInstanceId, jwt);

    assertThat(harness.outputQueueSize()).isEqualTo(2);
  }

  @Test
  void authGateDisabled_bypassesReplayProtectionEvenWhenModeIsStrict() throws Exception {
    TestHarness harness = createHarness(ReplayProtectionMode.STRICT, 600_000L, false);
    UUID processInstanceId = UUID.randomUUID();
    String jwt = harness.buildJwt("audit-no-auth-gate");

    harness.pipe(processInstanceId, jwt);
    harness.pipe(processInstanceId, jwt);

    assertThat(harness.outputQueueSize()).isEqualTo(2);
  }

  private static TestHarness createHarness(
      ReplayProtectionMode mode, long retentionMs, boolean engineRequiresAuthorization)
      throws Exception {
    KeyPair rsaKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    TaktConfiguration taktConfiguration = mock(TaktConfiguration.class);
    when(taktConfiguration.getTenantId()).thenReturn("test-tenant");
    when(taktConfiguration.getNamespace()).thenReturn("default");
    when(taktConfiguration.getPrefixed(any()))
        .thenAnswer(invocation -> "test-tenant.default." + invocation.getArgument(0, String.class));

    GlobalConfigStore globalConfigStore = new GlobalConfigStore();
    globalConfigStore.update(
        GlobalConfigurationDTO.builder()
            .engineRequiresAuthorization(engineRequiresAuthorization)
            .replayProtectionMode(mode)
            .replayProtectionRetentionMs(retentionMs)
            .build());

    PublicKeyProvider publicKeyProvider = mock(PublicKeyProvider.class);
    when(publicKeyProvider.getKey(PLATFORM_KID)).thenReturn(rsaKeyPair.getPublic());

    EngineAuthorizationService authorizationService =
        new EngineAuthorizationService(
            taktConfiguration,
            globalConfigStore,
            publicKeyProvider,
            new NonceStore(),
            mock(org.apache.kafka.streams.KafkaStreams.class));

    AtomicLong nowMs = new AtomicLong(Instant.parse("2026-04-20T12:00:00Z").toEpochMilli());
    Clock clock = new TestClock(nowMs);

    StreamsBuilder builder = new StreamsBuilder();
    builder.addStateStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(REPLAY_STORE_NAME),
                org.apache.kafka.common.serialization.Serdes.String(),
                org.apache.kafka.common.serialization.Serdes.Long())
            .withLoggingDisabled());

    builder.stream(
            INPUT_TOPIC,
            Consumed.with(
                TopologyProducer.PROCESS_INSTANCE_KEY_SERDE,
                TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE))
        .filter((_, envelope) -> envelope.replayRoutingKeyHint() != null)
        .selectKey((_, envelope) -> envelope.replayRoutingKeyHint())
        .repartition(
            Repartitioned.<String, ProcessInstanceTriggerEnvelope>as(
                    "replay-protection-test-repartition")
                .withKeySerde(org.apache.kafka.common.serialization.Serdes.String())
                .withValueSerde(TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE))
        .process(
            () -> new ReplayProtectionProcessor(clock, authorizationService, REPLAY_STORE_NAME),
            REPLAY_STORE_NAME)
        .to(
            OUTPUT_TOPIC,
            Produced.with(
                TopologyProducer.PROCESS_INSTANCE_KEY_SERDE,
                TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE));

    Properties props = new Properties();
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, "replay-protection-processor-test-" + mode);
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
    props.put(
        StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
        org.apache.kafka.common.serialization.Serdes.StringSerde.class);
    props.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
        org.apache.kafka.common.serialization.Serdes.ByteArraySerde.class);

    TopologyTestDriver driver = new TopologyTestDriver(builder.build(), props);
    TestInputTopic<UUID, ProcessInstanceTriggerEnvelope> inputTopic =
        driver.createInputTopic(
            INPUT_TOPIC,
            TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.serializer(),
            TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE.serializer());
    TestOutputTopic<UUID, ProcessInstanceTriggerEnvelope> outputTopic =
        driver.createOutputTopic(
            OUTPUT_TOPIC,
            TopologyProducer.PROCESS_INSTANCE_KEY_SERDE.deserializer(),
            TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE.deserializer());
    return new TestHarness(driver, inputTopic, outputTopic, rsaKeyPair, nowMs);
  }

  private record TestHarness(
      TopologyTestDriver driver,
      TestInputTopic<UUID, ProcessInstanceTriggerEnvelope> inputTopic,
      TestOutputTopic<UUID, ProcessInstanceTriggerEnvelope> outputTopic,
      KeyPair rsaKeyPair,
      AtomicLong nowMs) {

    private void pipe(UUID processInstanceId, String jwt) {
      RecordHeaders headers = new RecordHeaders();
      headers.add(Constants.HEADER_AUTHORIZATION, jwt.getBytes(StandardCharsets.UTF_8));
      inputTopic.pipeInput(
          new TestRecord<>(
              processInstanceId,
              new ProcessInstanceTriggerEnvelope(startCommand(processInstanceId), false, null),
              headers,
              Instant.ofEpochMilli(nowMs.get())));
    }

    private long outputQueueSize() {
      return outputTopic.getQueueSize();
    }

    private void advanceMillis() {
      nowMs.addAndGet(2000L);
      driver.advanceWallClockTime(Duration.ofMillis(2000L));
    }

    private String buildJwt(String auditId) {
      return Jwts.builder()
          .header()
          .keyId(PLATFORM_KID)
          .and()
          .subject("user-replay")
          .issuer(ISSUER)
          .claim("action", "START")
          .claim("version", -1)
          .claim("namespaceId", UUID.randomUUID().toString())
          .claim("auditId", auditId)
          .claim("processDefinitionId", "proc")
          .expiration(Date.from(Instant.now().plusSeconds(300)))
          .signWith(rsaKeyPair.getPrivate())
          .compact();
    }

    private static StartCommandDTO startCommand(UUID processInstanceId) {
      return new StartCommandDTO(
          processInstanceId,
          null,
          null,
          new ProcessDefinitionKey("proc", -1),
          VariablesDTO.empty());
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
