/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.dlq;

import io.taktx.Topics;
import io.taktx.dto.DlqLineageDTO;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.DlqReplayResult;
import io.taktx.dto.ReplayValidationPolicy;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.security.MessageSigningService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

/**
 * Replay processor for the {@code dlq.replay} topic (DLQ-010 through DLQ-014).
 *
 * <p>Consumes {@link DlqReplayCommand} records submitted by operators and executes the following
 * validation pipeline before forwarding the corrected record:
 *
 * <ol>
 *   <li><b>Destination topic safety (DLQ-011)</b>: the target topic must be a whitelisted ingress
 *       surface name ({@link #ALLOWED_INGRESS_SURFACES}) and may only be prefixed with the engine's
 *       own tenant + namespace.
 *   <li><b>Schema compatibility (DLQ-013)</b>: when {@code expectedSchemaVersion} is set and does
 *       not match {@link #SUPPORTED_SCHEMA_VERSION}, behaviour is governed by the command's {@link
 *       ReplayValidationPolicy}: {@code STRICT} rejects, {@code OPERATOR_OVERRIDE} logs a warning
 *       and proceeds with an audit marker.
 *   <li><b>ENGINE signing (DLQ-012)</b>: the corrected payload is signed with the engine's active
 *       Ed25519 key. {@code replaySigner} and {@code replaySignatureKeyId} are populated in the
 *       emitted {@link DlqReplayResult}.
 *   <li><b>Lineage headers</b>: every forwarded record carries {@code X-DLQ-Lineage-Ref}, {@code
 *       X-DLQ-Correction-Id} and {@code X-DLQ-Source-Offset}.
 *   <li><b>Dry-run (DLQ-014)</b>: when {@code command.isDryRun() == true} all validation steps run
 *       but no {@link DlqReplayForwardRecord} is emitted; only a result with status {@code
 *       DRY_RUN_PASSED} or {@code DRY_RUN_FAILED} is produced.
 * </ol>
 *
 * <p>Two output types are forwarded downstream:
 *
 * <ul>
 *   <li>{@link DlqReplayResult} — always, keyed by {@code dlqEntryRef} → routed to {@code
 *       dlq.replay-results}.
 *   <li>{@link DlqReplayForwardRecord} — only on success + not dry-run → consumed by {@link
 *       DlqForwardingProcessor} and routed to the correct ingress topic.
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class DlqReplayProcessor implements Processor<String, DlqReplayCommand, Object, Object> {

  /** Schema version currently understood by this engine build. */
  static final int SUPPORTED_SCHEMA_VERSION = 1;

  static final String HEADER_DLQ_LINEAGE_REF = "X-DLQ-Lineage-Ref";
  static final String HEADER_DLQ_CORRECTION_ID = "X-DLQ-Correction-Id";
  static final String HEADER_DLQ_SOURCE_OFFSET = "X-DLQ-Source-Offset";
  private static final String HEADER_ENGINE_SIGNATURE = "X-TaktX-Signature";

  /**
   * Bare topic names (without tenant/namespace prefix) accepted as replay destinations. Mirrors the
   * final DLQ scope baseline.
   */
  static final Set<String> ALLOWED_INGRESS_SURFACES =
      Set.of(
          Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName(),
          Topics.MESSAGE_EVENT_TOPIC.getTopicName(),
          Topics.SIGNAL_TOPIC.getTopicName(),
          Topics.PROCESS_DEFINITION_ACTIVATION_TOPIC.getTopicName(),
          Topics.DMN_DEFINITION_ACTIVATION_TOPIC.getTopicName(),
          Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName(),
          Topics.DMN_DEFINITIONS_TRIGGER_TOPIC.getTopicName(),
          Topics.USER_TASK_RESPONSE_TOPIC.getTopicName());

  private final MessageSigningService messageSigningService;
  private final TaktConfiguration taktConfiguration;
  private final DlqObservabilityService observabilityService;

  private ProcessorContext<Object, Object> context;

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
  }

  @Override
  public void process(Record<String, DlqReplayCommand> replayRecord) {
    DlqReplayCommand command = replayRecord.value();
    if (command == null) {
      log.warn("⚠ Null DlqReplayCommand on dlq.replay — skipping");
      return;
    }

    long now = replayRecord.timestamp();
    String correctionId = UUID.randomUUID().toString();

    // ── DLQ-011: Destination topic safety ────────────────────────────────────
    String destinationTopic = command.getDestinationTopic();
    if (destinationTopic == null || destinationTopic.isBlank()) {
      log.warn(
          "⚠ DLQ replay rejected: destinationTopic is null or blank dlqEntryRef={}",
          command.getDlqEntryRef());
      emitResult(
          command,
          now,
          false,
          "FAILED",
          "destinationTopic is null or blank",
          null,
          null,
          correctionId);
      return;
    }
    if (!ALLOWED_INGRESS_SURFACES.contains(destinationTopic)) {
      log.warn(
          "⚠ DLQ replay rejected: destinationTopic='{}' is not an allowed ingress surface"
              + " dlqEntryRef={}",
          destinationTopic,
          command.getDlqEntryRef());
      emitResult(
          command,
          now,
          false,
          "FAILED",
          "destinationTopic '" + destinationTopic + "' is not an allowed ingress surface",
          null,
          null,
          correctionId);
      return;
    }
    String fullTargetTopic = taktConfiguration.getPrefixed(destinationTopic);

    // ── DLQ-013: Schema compatibility ────────────────────────────────────────
    Integer expectedSchema = command.getExpectedSchemaVersion();
    String compatibilityDecision = null;
    if (expectedSchema != null && expectedSchema != SUPPORTED_SCHEMA_VERSION) {
      if (command.getValidationPolicy() == ReplayValidationPolicy.STRICT) {
        log.warn(
            "⚠ DLQ replay rejected by STRICT schema policy: expectedSchemaVersion={}"
                + " supportedSchemaVersion={} dlqEntryRef={}",
            expectedSchema,
            SUPPORTED_SCHEMA_VERSION,
            command.getDlqEntryRef());
        emitResult(
            command,
            now,
            false,
            "FAILED",
            "Schema version mismatch: expected="
                + expectedSchema
                + " supported="
                + SUPPORTED_SCHEMA_VERSION,
            "INCOMPATIBLE",
            null,
            correctionId);
        return;
      }
      // OPERATOR_OVERRIDE: warn and continue
      compatibilityDecision = "OVERRIDE_ACCEPTED_SCHEMA_VERSION_MISMATCH";
      log.warn(
          "⚠ DLQ replay schema compatibility override (OPERATOR_OVERRIDE):"
              + " expectedSchemaVersion={} supportedSchemaVersion={}"
              + " dlqEntryRef={} overrideReason={}",
          expectedSchema,
          SUPPORTED_SCHEMA_VERSION,
          command.getDlqEntryRef(),
          command.getOverrideReason());
    } else if (expectedSchema != null) {
      compatibilityDecision = "COMPATIBLE";
    }

    // ── DLQ-012: ENGINE signing ───────────────────────────────────────────────
    byte[] payload =
        command.getCorrectedValueBytes() != null ? command.getCorrectedValueBytes() : new byte[0];
    String signatureHeaderValue = messageSigningService.signToHeaderValue(payload);
    String replaySignerKeyId = messageSigningService.getKeyId();
    String engineInstanceId =
        taktConfiguration.getTenantId() + "." + taktConfiguration.getNamespace();

    // Build headers for the replayed record (lineage + corrected + fresh signature)
    Map<String, byte[]> forwardHeaders =
        buildForwardHeaders(command, correctionId, signatureHeaderValue);

    // ── DLQ-014: Dry-run path ─────────────────────────────────────────────────
    if (command.isDryRun()) {
      log.info(
          "✅ DLQ replay dry-run passed: dlqEntryRef={} destination={} correctionId={}",
          command.getDlqEntryRef(),
          fullTargetTopic,
          correctionId);
      emitResult(
          command,
          now,
          true,
          "DRY_RUN_PASSED",
          "Dry-run validation passed; no record forwarded to " + fullTargetTopic,
          engineInstanceId,
          replaySignerKeyId,
          compatibilityDecision,
          correctionId);
      return;
    }

    // ── DLQ-010: Forward replayed record ─────────────────────────────────────
    DlqReplayForwardRecord forwardRecord =
        new DlqReplayForwardRecord(fullTargetTopic, payload, forwardHeaders);
    context.forward(new Record<>(null, forwardRecord, now));

    log.info(
        "✅ DLQ replay forwarded: dlqEntryRef={} destination={} correctionId={} keyId={}",
        command.getDlqEntryRef(),
        fullTargetTopic,
        correctionId,
        replaySignerKeyId);

    emitResult(
        command,
        now,
        false,
        "SUCCESS",
        "Replay forwarded to " + fullTargetTopic + " with correctionId=" + correctionId,
        engineInstanceId,
        replaySignerKeyId,
        compatibilityDecision,
        correctionId);
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private Map<String, byte[]> buildForwardHeaders(
      DlqReplayCommand command, String correctionId, String signatureHeaderValue) {
    Map<String, byte[]> headers = new HashMap<>();

    // Decode operator-provided corrected headers (Map<String, String> where values are base64).
    // Skip any existing X-TaktX-Signature — we replace it with a fresh ENGINE signature.
    if (command.getCorrectedHeaders() != null) {
      command
          .getCorrectedHeaders()
          .forEach(
              (key, base64Value) -> {
                if (HEADER_ENGINE_SIGNATURE.equals(key)) {
                  return; // will be replaced below
                }
                if (base64Value != null) {
                  try {
                    headers.put(key, Base64.getDecoder().decode(base64Value));
                  } catch (IllegalArgumentException _) {
                    // Plain UTF-8 text — attach as-is
                    headers.put(key, base64Value.getBytes(StandardCharsets.UTF_8));
                  }
                }
              });
    }

    // Lineage headers (DLQ-010 acceptance criterion)
    headers.put(
        HEADER_DLQ_LINEAGE_REF,
        (command.getDlqEntryRef() != null ? command.getDlqEntryRef() : "")
            .getBytes(StandardCharsets.UTF_8));
    headers.put(HEADER_DLQ_CORRECTION_ID, correctionId.getBytes(StandardCharsets.UTF_8));
    DlqLineageDTO lineage = command.getLineage();
    if (lineage != null && lineage.getSourceOffset() != null) {
      headers.put(
          HEADER_DLQ_SOURCE_OFFSET,
          lineage.getSourceOffset().toString().getBytes(StandardCharsets.UTF_8));
    }

    // Fresh ENGINE signature (DLQ-012)
    if (signatureHeaderValue != null) {
      headers.put(HEADER_ENGINE_SIGNATURE, signatureHeaderValue.getBytes(StandardCharsets.UTF_8));
    }

    return headers;
  }

  /** Emits a {@link DlqReplayResult} for the success, dry-run, or failure paths. */
  private void emitResult(
      DlqReplayCommand command,
      long nowMs,
      boolean dryRun,
      String status,
      String outcomeText,
      String replaySigner,
      String replaySignatureKeyId,
      String compatibilityDecision,
      String correctionId) {
    DlqReplayResult result = new DlqReplayResult();
    result.setDlqEntryRef(command.getDlqEntryRef());
    result.setOperatorId(command.getOperatorId());
    result.setReplayAtMs(nowMs);
    result.setStatus(status);
    result.setOutcomeText(outcomeText);
    result.setReplaySigner(replaySigner);
    result.setReplaySignatureKeyId(replaySignatureKeyId);
    result.setCompatibilityDecision(compatibilityDecision);
    result.setDryRun(dryRun);
    result.setLineageRef(command.getDlqEntryRef());
    result.setOverrideReason(command.getOverrideReason());
    result.setCorrectionId(correctionId);
    observabilityService.recordReplayOutcome(result);
    context.forward(new Record<>(command.getDlqEntryRef(), result, nowMs));
  }

  /** Convenience overload for paths that don't yet have signer info (failure before signing). */
  private void emitResult(
      DlqReplayCommand command,
      long nowMs,
      boolean dryRun,
      String status,
      String outcomeText,
      String compatibilityDecision,
      String replaySignatureKeyId,
      String correctionId) {
    emitResult(
        command,
        nowMs,
        dryRun,
        status,
        outcomeText,
        null,
        replaySignatureKeyId,
        compatibilityDecision,
        correctionId);
  }
}
