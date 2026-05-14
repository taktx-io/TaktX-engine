/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.DefinitionScheduleKeyDTO;
import io.taktx.dto.DefinitionsKey;
import io.taktx.dto.FlowElementsDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessDefinitionStateEnum;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.StartEventDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.dto.TimerEventDefinitionDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pi.DefinitionsCache;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link ProcessDefinitionActivationProcessor}.
 *
 * <p>SEC-004 acceptance criteria: verifies that deactivating a process definition that has a timer
 * start event forwards cancellation tombstones (null value) for all {@link TimeBucket} values, so
 * the schedule is removed from whichever bucket it was stored in.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ProcessDefinitionActivationProcessorTest {

  private static final long FIXED_CLOCK_MS = 1_700_000_000_000L;
  private static final String PROCESS_DEF_ID = "my-process";
  private static final int VERSION = 1;
  private static final String START_EVENT_ID = "startEvent1";
  private static final String TIMER_DEF_ID = "timerDef1";

  private ProcessorContext<Object, Object> context;
  private ProcessDefinitionActivationProcessor processor;

  @BeforeEach
  void setUp() {
    TaktConfiguration taktConfiguration = mock(TaktConfiguration.class);
    when(taktConfiguration.getPrefixed(Stores.GLOBAL_PROCESS_DEFINITION.getStorename()))
        .thenReturn(Stores.GLOBAL_PROCESS_DEFINITION.getStorename());

    Clock clock = Clock.fixed(Instant.ofEpochMilli(FIXED_CLOCK_MS), ZoneOffset.UTC);
    MessageSchedulerFactory schedulerFactory = mock(MessageSchedulerFactory.class);
    FeelExpressionHandler feelHandler = mock(FeelExpressionHandler.class);
    DefinitionsCache definitionsCache = mock(DefinitionsCache.class);

    context = mock(ProcessorContext.class);
    // deactivate() does not access the processDefinitionStore, so no stub needed.

    processor =
        new ProcessDefinitionActivationProcessor(
            taktConfiguration, schedulerFactory, context, clock, feelHandler, definitionsCache);
  }

  // ── helper ─────────────────────────────────────────────────────────────────

  private ProcessDefinitionDTO buildProcessDefinitionWithTimerStart() {
    TimerEventDefinitionDTO timerDef =
        new TimerEventDefinitionDTO(TIMER_DEF_ID, START_EVENT_ID, null, "PT1H", null);

    StartEventDTO startEvent =
        new StartEventDTO(
            START_EVENT_ID,
            PROCESS_DEF_ID,
            "start",
            Set.of(),
            Set.of(),
            Set.of(timerDef),
            null,
            true);

    FlowElementsDTO flowElements = new FlowElementsDTO(Map.of(START_EVENT_ID, startEvent));

    ProcessDTO rootProcess = new ProcessDTO(PROCESS_DEF_ID, null, null, flowElements);

    DefinitionsKey definitionsKey = new DefinitionsKey(PROCESS_DEF_ID, "hash1");
    ParsedDefinitionsDTO definitions =
        ParsedDefinitionsDTO.builder()
            .definitionsKey(definitionsKey)
            .rootProcess(rootProcess)
            .messages(Map.of())
            .escalations(Map.of())
            .errors(Map.of())
            .signals(Map.of())
            .build();

    return new ProcessDefinitionDTO(definitions, VERSION, ProcessDefinitionStateEnum.ACTIVE);
  }

  private ProcessDefinitionDTO buildProcessDefinitionWithoutTimerStart() {
    StartEventDTO startEvent =
        new StartEventDTO(
            START_EVENT_ID,
            PROCESS_DEF_ID,
            "start",
            Set.of(),
            Set.of(),
            Set.of(), // no event definitions
            null,
            true);

    FlowElementsDTO flowElements = new FlowElementsDTO(Map.of(START_EVENT_ID, startEvent));

    ProcessDTO rootProcess = new ProcessDTO(PROCESS_DEF_ID, null, null, flowElements);

    DefinitionsKey definitionsKey = new DefinitionsKey(PROCESS_DEF_ID, "hash2");
    ParsedDefinitionsDTO definitions =
        ParsedDefinitionsDTO.builder()
            .definitionsKey(definitionsKey)
            .rootProcess(rootProcess)
            .messages(Map.of())
            .escalations(Map.of())
            .errors(Map.of())
            .signals(Map.of())
            .build();

    return new ProcessDefinitionDTO(definitions, VERSION, ProcessDefinitionStateEnum.ACTIVE);
  }

  // ── SEC-004 cancellation tests ──────────────────────────────────────────────

  /**
   * SEC-004: Deactivating a process definition with a timer start event must forward one tombstone
   * (null value) per {@link TimeBucket} so the scheduler removes the entry regardless of which
   * bucket it was originally stored in.
   */
  @Test
  void deactivate_withTimerStartEvent_forwardsTombstonesForAllTimeBuckets() {
    ProcessDefinitionDTO processDefinition = buildProcessDefinitionWithTimerStart();
    ProcessDefinitionKey expectedKey = new ProcessDefinitionKey(PROCESS_DEF_ID, VERSION);

    processor.deactivate(processDefinition);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context, atLeastOnce()).forward(captor.capture());

    // Collect all forwarded DefinitionScheduleKeyDTO tombstones
    List<DefinitionScheduleKeyDTO> tombstoneKeys =
        captor.getAllValues().stream()
            .filter(r -> r.key() instanceof DefinitionScheduleKeyDTO && r.value() == null)
            .map(r -> (DefinitionScheduleKeyDTO) r.key())
            .collect(Collectors.toList());

    // Exactly one tombstone per TimeBucket must be present
    Set<TimeBucket> coveredBuckets =
        tombstoneKeys.stream().map(ScheduleKeyDTO::getTimeBucket).collect(Collectors.toSet());

    assertThat(coveredBuckets)
        .as("must cover every TimeBucket")
        .containsExactlyInAnyOrderElementsOf(Arrays.asList(TimeBucket.values()));

    // All tombstone keys must reference the correct process definition and start event
    assertThat(tombstoneKeys)
        .allSatisfy(
            key -> {
              assertThat(key.getProcessDefinitionKey()).isEqualTo(expectedKey);
              assertThat(key.getFlowNodeId()).isEqualTo(START_EVENT_ID);
            });
  }

  /**
   * SEC-004: Deactivating a process definition that has a timer start event with {@code startEvent}
   * already in INACTIVE state must be a no-op (early return in {@link
   * ProcessDefinitionActivationProcessor#deactivate}).
   */
  @Test
  void deactivate_alreadyInactive_isNoOp() {
    ProcessDefinitionDTO alreadyInactive =
        new ProcessDefinitionDTO(
            buildProcessDefinitionWithTimerStart().getDefinitions(),
            VERSION,
            ProcessDefinitionStateEnum.INACTIVE);

    processor.deactivate(alreadyInactive);

    verify(context, never()).forward(any());
  }

  /**
   * SEC-004: Deactivating a process definition whose start event has no timer definitions must not
   * forward any schedule tombstones (but may still forward the deactivated definition record).
   */
  @Test
  void deactivate_withoutTimerStartEvent_forwardsNoScheduleTombstones() {
    ProcessDefinitionDTO processDefinition = buildProcessDefinitionWithoutTimerStart();

    processor.deactivate(processDefinition);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context, atLeastOnce()).forward(captor.capture());

    long tombstoneCount =
        captor.getAllValues().stream()
            .filter(r -> r.key() instanceof DefinitionScheduleKeyDTO && r.value() == null)
            .count();

    assertThat(tombstoneCount)
        .as("no schedule tombstones should be forwarded when there are no timer definitions")
        .isZero();
  }
}
