/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pd;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.DefinitionScheduleKeyDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.OneTimeScheduleDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.SchedulableMessageDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.dlq.DlqObservabilityService;
import io.taktx.engine.pi.ProcessingStatistics;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.engine.security.ProtectedDataPlaneParticipationGuard;
import io.taktx.security.AuthorizationTokenException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.Punctuator;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for excluded-topic failure observability in {@link ScheduleProcessor} (DLQ-T08 / DLQ-018A).
 *
 * <p>Verifies that a {@code schedule-command} processing failure:
 *
 * <ul>
 *   <li>increments the {@code taktx.excluded.topic.failures} counter via {@link
 *       DlqObservabilityService#recordExcludedTopicFailure},
 *   <li>does <em>not</em> publish any DLQ record, and
 *   <li>does <em>not</em> forward the output (stream thread keeps running).
 * </ul>
 */
@SuppressWarnings("unchecked")
class ScheduleProcessorExcludedTopicTest {

  private static final String SCHEDULE_TOPIC = "acme.prod.schedule-commands";

  private EngineAuthorizationService engineAuthorizationService;
  private KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> store;
  private ProcessorContext<Object, SchedulableMessageDTO> context;
  private DlqObservabilityService dlqObservabilityService;
  private ProtectedDataPlaneParticipationGuard protectedDataPlaneParticipationGuard;
  private ScheduleProcessor scheduleProcessor;

  @BeforeEach
  void setUp() {
    engineAuthorizationService = mock(EngineAuthorizationService.class);
    ProcessingStatistics processingStatistics = mock(ProcessingStatistics.class);
    store = mock(KeyValueStore.class);
    context = mock(ProcessorContext.class);
    dlqObservabilityService = mock(DlqObservabilityService.class);
    protectedDataPlaneParticipationGuard = mock(ProtectedDataPlaneParticipationGuard.class);

    when(context.schedule(any(), eq(PunctuationType.WALL_CLOCK_TIME), any(Punctuator.class)))
        .thenReturn(mock(Cancellable.class));

    Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneId.systemDefault());
    scheduleProcessor =
        new ScheduleProcessor(
            clock,
            true,
            (ignored, _) -> store,
            new TimeBucket[] {TimeBucket.MINUTE},
            processingStatistics,
            SCHEDULE_TOPIC,
            new ScheduleProcessor.SecurityServices(
                engineAuthorizationService,
                dlqObservabilityService,
                protectedDataPlaneParticipationGuard));
    scheduleProcessor.init(context);

    when(protectedDataPlaneParticipationGuard.evaluate())
        .thenReturn(ProtectedDataPlaneParticipationGuard.Decision.permit());
  }

  @Test
  void processingFailureOnEngineInternalTopic_incrementsExcludedTopicCounter() {
    DefinitionScheduleKeyDTO scheduleKey = scheduleKey();
    MessageScheduleDTO schedule = oneTimeSchedule();
    RecordHeaders headers = signedHeaders("engine-key-1");

    // Auth passes but BucketProcessor.process() throws a RuntimeException (engine defect)
    when(engineAuthorizationService.authorizeScheduleCommand(any(), any()))
        .thenReturn(activeEngineKey("engine-key-1"));
    doThrow(new RuntimeException("simulated bucket defect")).when(store).get(any());

    scheduleProcessor.process(
        new Record<>(
            scheduleKey,
            new ScheduleCommandEnvelope(schedule, true, null, null),
            999_000L,
            headers));

    // DLQ-018A: the excluded-topic failure counter must be incremented once
    verify(dlqObservabilityService).recordExcludedTopicFailure("schedule-commands");
    // No record must be forwarded (the stream thread should survive silently)
    verify(context, never()).forward(any());
  }

  @Test
  void processingSuccess_doesNotIncrementExcludedTopicCounter() {
    DefinitionScheduleKeyDTO scheduleKey = scheduleKey();
    MessageScheduleDTO schedule = oneTimeSchedule();
    RecordHeaders headers = signedHeaders("engine-key-2");

    when(engineAuthorizationService.authorizeScheduleCommand(any(), any()))
        .thenReturn(activeEngineKey("engine-key-2"));

    scheduleProcessor.process(
        new Record<>(
            scheduleKey,
            new ScheduleCommandEnvelope(schedule, true, null, null),
            999_000L,
            headers));

    verify(dlqObservabilityService, never()).recordExcludedTopicFailure(any());
  }

  @Test
  void authorizationFailureOnEngineInternalTopic_incrementsExcludedTopicCounterWithoutForwarding() {
    DefinitionScheduleKeyDTO scheduleKey = scheduleKey();
    MessageScheduleDTO schedule = oneTimeSchedule();
    RecordHeaders headers = signedHeaders("client-key-1");

    when(engineAuthorizationService.authorizeScheduleCommand(any(), any()))
        .thenThrow(new AuthorizationTokenException("client signer not allowed"));

    scheduleProcessor.process(
        new Record<>(
            scheduleKey,
            new ScheduleCommandEnvelope(schedule, true, null, null),
            999_000L,
            headers));

    verify(dlqObservabilityService).recordExcludedTopicFailure("schedule-commands");
    verify(context, never()).forward(any());
  }

  @Test
  void protectedDataPlaneBlock_incrementsExcludedTopicCounterWithoutForwarding() {
    DefinitionScheduleKeyDTO scheduleKey = scheduleKey();
    MessageScheduleDTO schedule = oneTimeSchedule();
    RecordHeaders headers = signedHeaders("engine-key-3");

    when(engineAuthorizationService.authorizeScheduleCommand(any(), any()))
        .thenReturn(activeEngineKey("engine-key-3"));
    when(protectedDataPlaneParticipationGuard.evaluate())
        .thenReturn(
            ProtectedDataPlaneParticipationGuard.Decision.blocked(
                ProtectedDataPlaneParticipationGuard.ENGINE_SIGNING_UNAVAILABLE, "engine not ready"));

    scheduleProcessor.process(
        new Record<>(
            scheduleKey,
            new ScheduleCommandEnvelope(schedule, true, null, null),
            999_000L,
            headers));

    verify(dlqObservabilityService).recordExcludedTopicFailure("schedule-commands");
    verify(context, never()).forward(any());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private DefinitionScheduleKeyDTO scheduleKey() {
    return new DefinitionScheduleKeyDTO(
        new ProcessDefinitionKey("proc", 1), "timer-start", TimeBucket.MINUTE);
  }

  private OneTimeScheduleDTO oneTimeSchedule() {
    return new OneTimeScheduleDTO(
        new StartCommandDTO(
            UUID.randomUUID(),
            null,
            null,
            new ProcessDefinitionKey("proc", -1),
            VariablesDTO.empty()),
        1_000_000L,
        1_060_000L);
  }

  private RecordHeaders signedHeaders(String keyId) {
    RecordHeaders headers = new RecordHeaders();
    headers.add("tx-sig", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));
    return headers;
  }

  private SigningKeyDTO activeEngineKey(String keyId) {
    return SigningKeyDTO.builder()
        .keyId(keyId)
        .publicKeyBase64("dummy")
        .algorithm("Ed25519")
        .role(KeyRole.ENGINE)
        .build();
  }
}
