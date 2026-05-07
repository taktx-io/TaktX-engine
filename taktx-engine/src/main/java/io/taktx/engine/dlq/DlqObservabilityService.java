/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.dlq;

import io.micrometer.core.instrument.MeterRegistry;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqReplayResult;
import io.taktx.dto.DlqSeverity;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralised observability service for DLQ entries and replay outcomes (DLQ-015, DLQ-017,
 * DLQ-018A).
 *
 * <p>Emits:
 *
 * <ul>
 *   <li>{@code taktx.dlq.entries} — counter tagged by {@code severity}, {@code reason_code}, {@code
 *       source_topic}, {@code capture_stage}.
 *   <li>{@code taktx.dlq.replay.outcomes} — counter tagged by {@code status}.
 *   <li>{@code taktx.excluded.topic.failures} — counter tagged by {@code topic_group}.
 * </ul>
 *
 * <p>Log levels follow severity: {@code CRITICAL} → {@code ERROR}, {@code HIGH} → {@code WARN},
 * {@code MEDIUM}/{@code LOW} → {@code INFO}.
 */
@Singleton
@RequiredArgsConstructor
@Slf4j
public class DlqObservabilityService {

  private static final String METRIC_DLQ_ENTRIES = "taktx.dlq.entries";
  private static final String METRIC_DLQ_REPLAY_OUTCOMES = "taktx.dlq.replay.outcomes";
  private static final String METRIC_EXCLUDED_TOPIC_FAILURES = "taktx.excluded.topic.failures";

  private final MeterRegistry meterRegistry;

  @PostConstruct
  void init() {
    // Pre-register the counter templates to avoid first-hit cardinality gaps in dashboards.
    for (DlqSeverity severity : DlqSeverity.values()) {
      meterRegistry.counter(
          METRIC_DLQ_ENTRIES,
          "severity",
          severity.name(),
          "reason_code",
          "_init_",
          "source_topic",
          "_init_",
          "capture_stage",
          "_init_");
    }
    for (String status : new String[] {"SUCCESS", "FAILED", "DRY_RUN_PASSED", "DRY_RUN_FAILED"}) {
      meterRegistry.counter(METRIC_DLQ_REPLAY_OUTCOMES, "status", status);
    }
  }

  // ── DLQ-015: DLQ entry observability ─────────────────────────────────────

  /**
   * Records a DLQ entry metric and emits a severity-leveled log line.
   *
   * @param envelope the envelope that was just published to the {@code dlq} topic
   */
  public void recordDlqEntry(DlqEnvelope envelope) {
    String severity = envelope.getSeverity() != null ? envelope.getSeverity().name() : "UNKNOWN";
    String reasonCode =
        envelope.getReasonCode() != null ? envelope.getReasonCode().name() : "UNKNOWN";
    String sourceTopic = envelope.getSourceTopic() != null ? envelope.getSourceTopic() : "unknown";
    String captureStage =
        envelope.getCaptureStage() != null ? envelope.getCaptureStage().name() : "UNKNOWN";

    meterRegistry
        .counter(
            METRIC_DLQ_ENTRIES,
            "severity",
            severity,
            "reason_code",
            reasonCode,
            "source_topic",
            sourceTopic,
            "capture_stage",
            captureStage)
        .increment();

    String logMsg =
        "DLQ entry published"
            + " sourceTopic={} reasonCode={} severity={} captureStage={}"
            + " engineInstanceId={} sourceOffset={} sourceMessageHash={}";

    DlqSeverity sev = envelope.getSeverity();
    switch (sev) {
      case DlqSeverity.CRITICAL ->
          log.error(
              logMsg,
              sourceTopic,
              reasonCode,
              severity,
              captureStage,
              envelope.getEngineInstanceId(),
              envelope.getSourceOffset(),
              envelope.getSourceMessageHash());
      case DlqSeverity.HIGH ->
          log.warn(
              logMsg,
              sourceTopic,
              reasonCode,
              severity,
              captureStage,
              envelope.getEngineInstanceId(),
              envelope.getSourceOffset(),
              envelope.getSourceMessageHash());
      default ->
          log.info(
              logMsg,
              sourceTopic,
              reasonCode,
              severity,
              captureStage,
              envelope.getEngineInstanceId(),
              envelope.getSourceOffset(),
              envelope.getSourceMessageHash());
    }
  }

  // ── DLQ-017: Replay outcome audit ────────────────────────────────────────

  /**
   * Records a replay outcome metric and emits a structured audit log line.
   *
   * @param result the result just emitted to the {@code dlq.replay-results} topic
   */
  public void recordReplayOutcome(DlqReplayResult result) {
    String status = result.getStatus() != null ? result.getStatus() : "UNKNOWN";

    meterRegistry.counter(METRIC_DLQ_REPLAY_OUTCOMES, "status", status).increment();

    String logMsg =
        "DLQ replay outcome"
            + " dlqEntryRef={} operatorId={} status={} dryRun={}"
            + " compatibilityDecision={} replaySigner={} replaySignatureKeyId={} overrideReason={}";

    if ("FAILED".equals(status) || "DRY_RUN_FAILED".equals(status)) {
      log.warn(
          logMsg,
          result.getDlqEntryRef(),
          result.getOperatorId(),
          status,
          result.isDryRun(),
          result.getCompatibilityDecision(),
          result.getReplaySigner(),
          result.getReplaySignatureKeyId(),
          result.getOverrideReason());
    } else {
      log.info(
          logMsg,
          result.getDlqEntryRef(),
          result.getOperatorId(),
          status,
          result.isDryRun(),
          result.getCompatibilityDecision(),
          result.getReplaySigner(),
          result.getReplaySignatureKeyId(),
          result.getOverrideReason());
    }
  }

  // ── DLQ-018A: Excluded topic failure observability ────────────────────────

  /**
   * Increments the excluded-topic failure counter for non-DLQ topics.
   *
   * @param topicGroup bare topic name or logical group (e.g. {@code "schedule-commands"})
   */
  public void recordExcludedTopicFailure(String topicGroup) {
    meterRegistry.counter(METRIC_EXCLUDED_TOPIC_FAILURES, "topic_group", topicGroup).increment();
  }
}
