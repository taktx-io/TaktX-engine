/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.dlq;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.taktx.dto.DlqCaptureStage;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqReasonCode;
import io.taktx.dto.DlqReplayResult;
import io.taktx.dto.DlqSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DlqObservabilityService} (DLQ-T07).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Metric counter tags (severity, reasonCode, sourceTopic, captureStage) for DLQ entries.
 *   <li>Replay outcome counter tags (status).
 *   <li>Excluded-topic failure counter tag (topicGroup).
 * </ul>
 */
class DlqObservabilityServiceTest {

  private MeterRegistry meterRegistry;
  private DlqObservabilityService service;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service = new DlqObservabilityService(meterRegistry);
    service.init();
  }

  // ── DLQ-015: DLQ entry metric ─────────────────────────────────────────────

  @Test
  void recordDlqEntry_criticalSeverity_incrementsCounterWithExpectedTags() {
    DlqEnvelope envelope =
        envelopeWith(
            DlqSeverity.CRITICAL,
            DlqReasonCode.REPLAY_DETECTED,
            "process-instance",
            DlqCaptureStage.PROCESSOR);

    service.recordDlqEntry(envelope);

    Counter counter =
        meterRegistry
            .find("taktx.dlq.entries")
            .tag("severity", "CRITICAL")
            .tag("reason_code", "REPLAY_DETECTED")
            .tag("source_topic", "process-instance")
            .tag("capture_stage", "PROCESSOR")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void recordDlqEntry_highSeverity_incrementsCounterWithExpectedTags() {
    DlqEnvelope envelope =
        envelopeWith(
            DlqSeverity.HIGH,
            DlqReasonCode.SIGNATURE_MISSING,
            "definitions",
            DlqCaptureStage.DESERIALIZER);

    service.recordDlqEntry(envelope);

    Counter counter =
        meterRegistry
            .find("taktx.dlq.entries")
            .tag("severity", "HIGH")
            .tag("reason_code", "SIGNATURE_MISSING")
            .tag("source_topic", "definitions")
            .tag("capture_stage", "DESERIALIZER")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void recordDlqEntry_mediumSeverity_incrementsCounterWithExpectedTags() {
    DlqEnvelope envelope =
        envelopeWith(
            DlqSeverity.MEDIUM,
            DlqReasonCode.CBOR_DECODE_ERROR,
            "message-event",
            DlqCaptureStage.DESERIALIZER);

    service.recordDlqEntry(envelope);

    Counter counter = meterRegistry.find("taktx.dlq.entries").tag("severity", "MEDIUM").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void recordDlqEntry_multipleEntries_accumulateOnSameCounter() {
    DlqEnvelope envelope =
        envelopeWith(
            DlqSeverity.HIGH,
            DlqReasonCode.SIGNATURE_VERIFICATION_FAILED,
            "signals",
            DlqCaptureStage.PROCESSOR);

    service.recordDlqEntry(envelope);
    service.recordDlqEntry(envelope);
    service.recordDlqEntry(envelope);

    Counter counter =
        meterRegistry
            .find("taktx.dlq.entries")
            .tag("severity", "HIGH")
            .tag("reason_code", "SIGNATURE_VERIFICATION_FAILED")
            .tag("source_topic", "signals")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(3.0);
  }

  // ── DLQ-017: Replay outcome metric ───────────────────────────────────────

  @Test
  void recordReplayOutcome_success_incrementsStatusCounter() {
    DlqReplayResult result =
        DlqReplayResult.builder()
            .dlqEntryRef("ref-001")
            .operatorId("alice")
            .status("SUCCESS")
            .replaySigner("engine.ns")
            .replaySignatureKeyId("key-42")
            .compatibilityDecision("COMPATIBLE")
            .build();

    service.recordReplayOutcome(result);

    Counter counter =
        meterRegistry.find("taktx.dlq.replay.outcomes").tag("status", "SUCCESS").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void recordReplayOutcome_failed_incrementsStatusCounter() {
    DlqReplayResult result =
        DlqReplayResult.builder().dlqEntryRef("ref-002").operatorId("bob").status("FAILED").build();

    service.recordReplayOutcome(result);

    Counter counter =
        meterRegistry.find("taktx.dlq.replay.outcomes").tag("status", "FAILED").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void recordReplayOutcome_dryRunPassed_incrementsStatusCounter() {
    DlqReplayResult result =
        DlqReplayResult.builder()
            .dlqEntryRef("ref-003")
            .operatorId("charlie")
            .status("DRY_RUN_PASSED")
            .dryRun(true)
            .build();

    service.recordReplayOutcome(result);

    Counter counter =
        meterRegistry.find("taktx.dlq.replay.outcomes").tag("status", "DRY_RUN_PASSED").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void recordReplayOutcome_overrideReasonPresent_counterIncrements() {
    DlqReplayResult result =
        DlqReplayResult.builder()
            .dlqEntryRef("ref-004")
            .operatorId("dave")
            .status("SUCCESS")
            .overrideReason("Schema migration approved by CAB-1234")
            .compatibilityDecision("OVERRIDE_ACCEPTED_SCHEMA_VERSION_MISMATCH")
            .build();

    service.recordReplayOutcome(result);

    Counter counter =
        meterRegistry.find("taktx.dlq.replay.outcomes").tag("status", "SUCCESS").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  // ── DLQ-018A: Excluded topic failure metric ───────────────────────────────

  @Test
  void recordExcludedTopicFailure_incrementsCounterWithTopicGroupTag() {
    service.recordExcludedTopicFailure("schedule-commands");

    Counter counter =
        meterRegistry
            .find("taktx.excluded.topic.failures")
            .tag("topic_group", "schedule-commands")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void recordExcludedTopicFailure_differentGroups_separateCounters() {
    service.recordExcludedTopicFailure("schedule-commands");
    service.recordExcludedTopicFailure("schedule-commands");
    service.recordExcludedTopicFailure("taktx-configuration");

    Counter scheduleCounter =
        meterRegistry
            .find("taktx.excluded.topic.failures")
            .tag("topic_group", "schedule-commands")
            .counter();
    Counter configCounter =
        meterRegistry
            .find("taktx.excluded.topic.failures")
            .tag("topic_group", "taktx-configuration")
            .counter();

    assertThat(scheduleCounter).isNotNull();
    assertThat(scheduleCounter.count()).isEqualTo(2.0);
    assertThat(configCounter).isNotNull();
    assertThat(configCounter.count()).isEqualTo(1.0);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static DlqEnvelope envelopeWith(
      DlqSeverity severity,
      DlqReasonCode reasonCode,
      String sourceTopic,
      DlqCaptureStage captureStage) {
    DlqEnvelope env = new DlqEnvelope();
    env.setSeverity(severity);
    env.setReasonCode(reasonCode);
    env.setSourceTopic(sourceTopic);
    env.setCaptureStage(captureStage);
    env.setEngineInstanceId("engine-test");
    return env;
  }
}
