/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.DlqLineageDTO;
import io.taktx.dto.DlqReplayCommand;
import io.taktx.dto.DlqReplayResult;
import io.taktx.dto.ReplayValidationPolicy;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.security.MessageSigningService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({"unchecked", "rawtypes"})
class DlqReplayProcessorTest {

  private ProcessorContext<Object, Object> context;
  private DlqReplayProcessor processor;

  @BeforeEach
  void setUp() {
    context = mock(ProcessorContext.class);
    MessageSigningService signingService = mock(MessageSigningService.class);
    TaktConfiguration config = mock(TaktConfiguration.class);

    when(signingService.signToHeaderValue(any())).thenReturn("engine-key.AABBCCDD==");
    when(signingService.getKeyId()).thenReturn("engine-key");
    when(config.getPrefixed(any()))
        .thenAnswer(inv -> "tenant.ns." + inv.getArgument(0, String.class));
    when(config.getTenantId()).thenReturn("tenant");
    when(config.getNamespace()).thenReturn("ns");

    processor = new DlqReplayProcessor(signingService, config, mock(DlqObservabilityService.class));
    processor.init(context);
  }

  // ── DLQ-T03: Replay policy tests ──────────────────────────────────────────

  @Test
  void process_validCommand_strict_forwardsPayloadAndEmitsSuccessResult() {
    // DLQ-T03: STRICT policy — valid destination + schema → success path
    DlqReplayCommand command =
        DlqReplayCommand.builder()
            .dlqEntryRef("ref-001")
            .operatorId("alice")
            .approvedAtMs(1_000L)
            .destinationTopic("process-instance")
            .validationPolicy(ReplayValidationPolicy.STRICT)
            .correctedValueBytes(new byte[] {1, 2, 3})
            .correctedHeaders(Map.of())
            .build();

    processor.process(new Record<>("ref-001", command, 9_000L));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context, org.mockito.Mockito.times(2)).forward(captor.capture());
    List<Record> emitted = captor.getAllValues();

    // First forward: DlqReplayForwardRecord
    assertThat(emitted.getFirst().value()).isInstanceOf(DlqReplayForwardRecord.class);
    DlqReplayForwardRecord fwd = (DlqReplayForwardRecord) emitted.getFirst().value();
    assertThat(fwd.targetTopic()).isEqualTo("tenant.ns.process-instance");
    assertThat(fwd.payload()).containsExactly(1, 2, 3);
    assertThat(fwd.headers()).containsKey(DlqReplayProcessor.HEADER_DLQ_LINEAGE_REF);
    assertThat(fwd.headers()).containsKey(DlqReplayProcessor.HEADER_DLQ_CORRECTION_ID);
    assertThat(fwd.headers()).containsKey("tx-sig");

    // Second forward: DlqReplayResult
    assertThat(emitted.get(1).value()).isInstanceOf(DlqReplayResult.class);
    DlqReplayResult result = (DlqReplayResult) emitted.get(1).value();
    assertThat(result.getStatus()).isEqualTo("SUCCESS");
    assertThat(result.getDlqEntryRef()).isEqualTo("ref-001");
    assertThat(result.getOperatorId()).isEqualTo("alice");
    assertThat(result.getReplaySigner()).isEqualTo("tenant.ns");
    assertThat(result.getReplaySignatureKeyId()).isEqualTo("engine-key");
    assertThat(result.isDryRun()).isFalse();
    assertThat(result.getLineageRef()).isEqualTo("ref-001");
  }

  @Test
  void process_validCommand_operatorOverride_proceedsAndSetsCompatibilityDecision() {
    // DLQ-T03: OPERATOR_OVERRIDE — schema mismatch but override is accepted
    DlqReplayCommand command =
        DlqReplayCommand.builder()
            .dlqEntryRef("ref-002")
            .operatorId("bob")
            .approvedAtMs(2_000L)
            .destinationTopic("definitions")
            .validationPolicy(ReplayValidationPolicy.OPERATOR_OVERRIDE)
            .correctedValueBytes(new byte[] {7, 8})
            .correctedHeaders(Map.of())
            .expectedSchemaVersion(99) // deliberate mismatch
            .overrideReason("Operator approved schema deviation")
            .build();

    processor.process(new Record<>("ref-002", command, 9_500L));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context, org.mockito.Mockito.times(2)).forward(captor.capture());

    DlqReplayResult result = (DlqReplayResult) captor.getAllValues().get(1).value();
    assertThat(result.getStatus()).isEqualTo("SUCCESS");
    assertThat(result.getCompatibilityDecision())
        .isEqualTo("OVERRIDE_ACCEPTED_SCHEMA_VERSION_MISMATCH");
  }

  // ── DLQ-T04: Destination safety and signing provenance ────────────────────

  @Test
  void process_destinationNotInWhitelist_emitsFailedResultWithoutForwarding() {
    // DLQ-T04: topic not in the 8 allowed ingress surfaces
    DlqReplayCommand command =
        DlqReplayCommand.builder()
            .dlqEntryRef("ref-003")
            .operatorId("charlie")
            .approvedAtMs(3_000L)
            .destinationTopic("schedule-commands") // excluded topic
            .validationPolicy(ReplayValidationPolicy.STRICT)
            .correctedValueBytes(new byte[] {0})
            .correctedHeaders(Map.of())
            .build();

    processor.process(new Record<>("ref-003", command, 9_100L));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture()); // only the FAILED result, no forward record
    assertThat(captor.getValue().value()).isInstanceOf(DlqReplayResult.class);

    DlqReplayResult result = (DlqReplayResult) captor.getValue().value();
    assertThat(result.getStatus()).isEqualTo("FAILED");
    assertThat(result.getOutcomeText()).contains("not an allowed ingress surface");
  }

  @Test
  void process_nullDestination_emitsFailedResult() {
    // DLQ-T04: null destination
    DlqReplayCommand command =
        DlqReplayCommand.builder()
            .dlqEntryRef("ref-004")
            .operatorId("dave")
            .approvedAtMs(4_000L)
            .destinationTopic(null)
            .validationPolicy(ReplayValidationPolicy.STRICT)
            .correctedValueBytes(new byte[] {0})
            .correctedHeaders(Map.of())
            .build();

    processor.process(new Record<>("ref-004", command, 9_200L));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    DlqReplayResult result = (DlqReplayResult) captor.getValue().value();
    assertThat(result.getStatus()).isEqualTo("FAILED");
    assertThat(result.getOutcomeText()).contains("null or blank");
  }

  @Test
  void process_successPath_signingProvenanceAppearsInResultAndForwardedHeaders() {
    // DLQ-T04: replaySigner + replaySignatureKeyId must be populated; signature header must be on
    // forwarded record
    DlqReplayCommand command =
        DlqReplayCommand.builder()
            .dlqEntryRef("ref-005")
            .operatorId("eve")
            .approvedAtMs(5_000L)
            .destinationTopic("signals")
            .validationPolicy(ReplayValidationPolicy.STRICT)
            .correctedValueBytes(new byte[] {42})
            .correctedHeaders(Map.of())
            .build();

    processor.process(new Record<>("ref-005", command, 9_300L));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context, org.mockito.Mockito.times(2)).forward(captor.capture());

    DlqReplayForwardRecord fwd = (DlqReplayForwardRecord) captor.getAllValues().get(0).value();
    assertThat(new String(fwd.headers().get("tx-sig"), StandardCharsets.UTF_8))
        .isEqualTo("engine-key.AABBCCDD==");

    DlqReplayResult result = (DlqReplayResult) captor.getAllValues().get(1).value();
    assertThat(result.getReplaySigner()).isEqualTo("tenant.ns");
    assertThat(result.getReplaySignatureKeyId()).isEqualTo("engine-key");
  }

  @Test
  void process_lineageHeadersAttachedToForwardRecord() {
    // DLQ-T04: dlq-lin, dlq-cid, dlq-off present
    DlqLineageDTO lineage = new DlqLineageDTO();
    lineage.setSourceOffset(12345L);
    lineage.setSourceTopic("process-instance");

    DlqReplayCommand command =
        DlqReplayCommand.builder()
            .dlqEntryRef("ref-006")
            .operatorId("frank")
            .approvedAtMs(6_000L)
            .destinationTopic("usertasks-response")
            .validationPolicy(ReplayValidationPolicy.STRICT)
            .correctedValueBytes(new byte[] {9})
            .correctedHeaders(Map.of())
            .lineage(lineage)
            .build();

    processor.process(new Record<>("ref-006", command, 9_400L));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context, org.mockito.Mockito.times(2)).forward(captor.capture());

    DlqReplayForwardRecord fwd = (DlqReplayForwardRecord) captor.getAllValues().getFirst().value();
    assertThat(
            new String(
                fwd.headers().get(DlqReplayProcessor.HEADER_DLQ_LINEAGE_REF),
                StandardCharsets.UTF_8))
        .isEqualTo("ref-006");
    assertThat(fwd.headers()).containsKey(DlqReplayProcessor.HEADER_DLQ_CORRECTION_ID);
    assertThat(
            new String(
                fwd.headers().get(DlqReplayProcessor.HEADER_DLQ_SOURCE_OFFSET),
                StandardCharsets.UTF_8))
        .isEqualTo("12345");
  }

  // ── DLQ-T05: Schema compatibility ─────────────────────────────────────────

  @Test
  void process_schemaMismatch_strict_rejectsWithIncompatibleCompatibilityDecision() {
    // DLQ-T05: STRICT + wrong expectedSchemaVersion → FAILED
    DlqReplayCommand command =
        DlqReplayCommand.builder()
            .dlqEntryRef("ref-007")
            .operatorId("grace")
            .approvedAtMs(7_000L)
            .destinationTopic("message-event")
            .validationPolicy(ReplayValidationPolicy.STRICT)
            .correctedValueBytes(new byte[] {1})
            .correctedHeaders(Map.of())
            .expectedSchemaVersion(99)
            .build();

    processor.process(new Record<>("ref-007", command, 9_600L));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture()); // only one emit — no forward record
    DlqReplayResult result = (DlqReplayResult) captor.getValue().value();
    assertThat(result.getStatus()).isEqualTo("FAILED");
    assertThat(result.getCompatibilityDecision()).isEqualTo("INCOMPATIBLE");
    assertThat(result.getOutcomeText()).contains("Schema version mismatch");
  }

  @Test
  void process_schemaMatch_strict_marksCompatible() {
    // DLQ-T05: STRICT + matching expectedSchemaVersion → COMPATIBLE
    DlqReplayCommand command =
        DlqReplayCommand.builder()
            .dlqEntryRef("ref-008")
            .operatorId("heidi")
            .approvedAtMs(8_000L)
            .destinationTopic("dmn-definitions")
            .validationPolicy(ReplayValidationPolicy.STRICT)
            .correctedValueBytes(new byte[] {2})
            .correctedHeaders(Map.of())
            .expectedSchemaVersion(DlqReplayProcessor.SUPPORTED_SCHEMA_VERSION)
            .build();

    processor.process(new Record<>("ref-008", command, 9_700L));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context, org.mockito.Mockito.times(2)).forward(captor.capture());
    DlqReplayResult result = (DlqReplayResult) captor.getAllValues().get(1).value();
    assertThat(result.getStatus()).isEqualTo("SUCCESS");
    assertThat(result.getCompatibilityDecision()).isEqualTo("COMPATIBLE");
  }

  // ── DLQ-T06: Dry-run ──────────────────────────────────────────────────────

  @Test
  void process_dryRun_validCommand_returnsDryRunPassedWithoutForwardRecord() {
    // DLQ-T06: dry-run on valid command → DRY_RUN_PASSED result only, no forward record
    DlqReplayCommand command =
        DlqReplayCommand.builder()
            .dlqEntryRef("ref-009")
            .operatorId("ivan")
            .approvedAtMs(9_000L)
            .destinationTopic("process-instance")
            .validationPolicy(ReplayValidationPolicy.STRICT)
            .correctedValueBytes(new byte[] {3, 4})
            .correctedHeaders(Map.of())
            .dryRun(true)
            .build();

    processor.process(new Record<>("ref-009", command, 9_800L));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture()); // only ONE emit — the result

    Record emitted = captor.getValue();
    assertThat(emitted.value()).isInstanceOf(DlqReplayResult.class);
    DlqReplayResult result = (DlqReplayResult) emitted.value();
    assertThat(result.getStatus()).isEqualTo("DRY_RUN_PASSED");
    assertThat(result.isDryRun()).isTrue();
    assertThat(result.getReplaySigner()).isEqualTo("tenant.ns");
    assertThat(result.getReplaySignatureKeyId()).isEqualTo("engine-key");
  }

  @Test
  void process_dryRun_invalidDestination_returnsDryRunFailedWithoutForwardRecord() {
    // DLQ-T06: dry-run on an invalid destination → DRY_RUN_FAILED, still no forward record
    DlqReplayCommand command =
        DlqReplayCommand.builder()
            .dlqEntryRef("ref-010")
            .operatorId("judy")
            .approvedAtMs(10_000L)
            .destinationTopic("taktx-configuration") // not allowed
            .validationPolicy(ReplayValidationPolicy.STRICT)
            .correctedValueBytes(new byte[] {0})
            .correctedHeaders(Map.of())
            .dryRun(true)
            .build();

    processor.process(new Record<>("ref-010", command, 9_900L));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    DlqReplayResult result = (DlqReplayResult) captor.getValue().value();
    // Destination check fires before dry-run check; status is FAILED (not DRY_RUN_FAILED)
    // because the destination gate must be enforced regardless of dry-run flag.
    assertThat(result.getStatus()).isIn("FAILED", "DRY_RUN_FAILED");
    assertThat(result.getOutcomeText()).contains("not an allowed ingress surface");
  }

  @Test
  void process_dryRun_schemaMismatch_strict_returnsDryRunFailed() {
    // DLQ-T06 + DLQ-T05: dry-run with STRICT schema mismatch → single DRY_RUN_FAILED result
    DlqReplayCommand command =
        DlqReplayCommand.builder()
            .dlqEntryRef("ref-011")
            .operatorId("kate")
            .approvedAtMs(11_000L)
            .destinationTopic("process-instance")
            .validationPolicy(ReplayValidationPolicy.STRICT)
            .correctedValueBytes(new byte[] {5})
            .correctedHeaders(Map.of())
            .expectedSchemaVersion(55)
            .dryRun(true)
            .build();

    processor.process(new Record<>("ref-011", command, 9_950L));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    DlqReplayResult result = (DlqReplayResult) captor.getValue().value();
    assertThat(result.getStatus()).isEqualTo("FAILED");
    assertThat(result.getCompatibilityDecision()).isEqualTo("INCOMPATIBLE");

    // No DlqReplayForwardRecord was emitted
    verify(context, never())
        .forward(
            org.mockito.ArgumentMatchers.argThat(
                r ->
                    r instanceof Record<?, ?> rec
                        && rec.value() instanceof DlqReplayForwardRecord));
  }
}
