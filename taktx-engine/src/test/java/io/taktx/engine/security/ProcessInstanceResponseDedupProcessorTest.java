/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.generic.TopologyProducer;
import io.taktx.engine.pi.ProcessInstanceTriggerEnvelope;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

class ProcessInstanceResponseDedupProcessorTest {

  private static final String INPUT_TOPIC = "process-instance-response-dedup-input";
  private static final String OUTPUT_TOPIC = "process-instance-response-dedup-output";
  private static final String STORE_NAME = "process-instance-response-dedup-test-store";

  @Test
  void firstExternalTaskResponseWithMessageId_isForwarded_andDuplicateIsDropped() {
    try (TestHarness harness = newHarness(10_000L)) {
      UUID processInstanceId = UUID.randomUUID();
      ExternalTaskResponseTriggerDTO trigger =
          new ExternalTaskResponseTriggerDTO(
              processInstanceId,
              List.of(10L, 20L),
              "msg-1",
              new ExternalTaskResponseResultDTO(
                  ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
              VariablesDTO.empty());

      harness.pipe(processInstanceId, trigger, null);
      harness.pipe(processInstanceId, trigger, null);

      assertThat(harness.outputTopic.getQueueSize()).isEqualTo(1);
      assertThat(harness.outputTopic.readValue().trigger())
          .isInstanceOf(ExternalTaskResponseTriggerDTO.class);
      assertThat(harness.outputTopic.isEmpty()).isTrue();
    }
  }

  @Test
  void duplicateUserTaskResponseAfterRetentionExpiry_isAcceptedAgain() {
    try (TestHarness harness = newHarness(1_000L)) {
      UUID processInstanceId = UUID.randomUUID();
      UserTaskResponseTriggerDTO trigger =
          new UserTaskResponseTriggerDTO(
              processInstanceId,
              List.of(30L, 40L),
              "msg-user-1",
              new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null),
              VariablesDTO.empty());

      harness.pipe(processInstanceId, trigger, null);
      harness.advanceMillis(1_500L);
      harness.pipe(processInstanceId, trigger, null);

      assertThat(harness.outputTopic.getQueueSize()).isEqualTo(2);
      assertThat(harness.outputTopic.readValue().trigger())
          .isInstanceOf(UserTaskResponseTriggerDTO.class);
      assertThat(harness.outputTopic.readValue().trigger())
          .isInstanceOf(UserTaskResponseTriggerDTO.class);
    }
  }

  @Test
  void responseWithoutMessageId_fallsBackToSignatureAndPayloadHash() {
    try (TestHarness harness = newHarness(10_000L)) {
      UUID processInstanceId = UUID.randomUUID();
      ExternalTaskResponseTriggerDTO trigger =
          new ExternalTaskResponseTriggerDTO(
              processInstanceId,
              List.of(50L),
              null,
              new ExternalTaskResponseResultDTO(
                  ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
              VariablesDTO.empty());
      RecordHeaders headers = new RecordHeaders();
      headers.add(
          Constants.HEADER_ENGINE_SIGNATURE, "worker-key.sig-a".getBytes(StandardCharsets.UTF_8));

      harness.pipe(processInstanceId, trigger, headers);
      harness.pipe(processInstanceId, trigger, headers);

      assertThat(harness.outputTopic.getQueueSize()).isEqualTo(1);
    }
  }

  @Test
  void nonWorkerResponseTriggers_arePassedThroughUntouched() {
    try (TestHarness harness = newHarness(10_000L)) {
      UUID processInstanceId = UUID.randomUUID();
      ProcessInstanceTriggerDTO trigger =
          new io.taktx.dto.ContinueFlowElementTriggerDTO(
              processInstanceId, List.of(99L), "flow-1", VariablesDTO.empty());

      harness.pipe(processInstanceId, trigger, null);

      assertThat(harness.outputTopic.getQueueSize()).isEqualTo(1);
      assertThat(harness.outputTopic.readValue().trigger())
          .isInstanceOf(io.taktx.dto.ContinueFlowElementTriggerDTO.class);
    }
  }

  private static TestHarness newHarness(long retentionMs) {
    AtomicLong nowMs = new AtomicLong(Instant.parse("2026-05-14T12:00:00Z").toEpochMilli());
    Clock clock = new TestClock(nowMs);

    StreamsBuilder builder = new StreamsBuilder();
    builder.addStateStore(
        Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(STORE_NAME), Serdes.String(), Serdes.Long())
            .withLoggingDisabled());

    builder.stream(
            INPUT_TOPIC,
            Consumed.with(
                TopologyProducer.PROCESS_INSTANCE_KEY_SERDE,
                TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE))
        .process(
            () -> new ProcessInstanceResponseDedupProcessor(clock, retentionMs, STORE_NAME),
            STORE_NAME)
        .to(
            OUTPUT_TOPIC,
            Produced.with(
                TopologyProducer.PROCESS_INSTANCE_KEY_SERDE,
                TopologyProducer.PROCESS_INSTANCE_TRIGGER_ENVELOPE_SERDE));

    Properties props = new Properties();
    props.put(
        StreamsConfig.APPLICATION_ID_CONFIG, "process-instance-response-dedup-test-" + retentionMs);
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");

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
    return new TestHarness(driver, inputTopic, outputTopic, nowMs);
  }

  private record TestHarness(
      TopologyTestDriver driver,
      TestInputTopic<UUID, ProcessInstanceTriggerEnvelope> inputTopic,
      TestOutputTopic<UUID, ProcessInstanceTriggerEnvelope> outputTopic,
      AtomicLong nowMs)
      implements AutoCloseable {

    private void pipe(UUID key, ProcessInstanceTriggerDTO trigger, RecordHeaders headers) {
      inputTopic.pipeInput(
          new TestRecord<>(
              key,
              new ProcessInstanceTriggerEnvelope(new byte[0], trigger, false, null),
              headers,
              Instant.ofEpochMilli(nowMs.get())));
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
