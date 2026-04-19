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
import static org.mockito.Mockito.doAnswer;
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
import io.taktx.engine.pi.ProcessingStatistics;
import io.taktx.engine.security.EngineAuthorizationService;
import io.taktx.security.AuthorizationTokenException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.Punctuator;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleProcessorTest {

  private static final String SCHEDULE_TOPIC = "acme.prod.schedule-commands";

  private final Map<ScheduleKeyDTO, MessageScheduleDTO> storeMap = new HashMap<>();

  private EngineAuthorizationService engineAuthorizationService;
  private ProcessingStatistics processingStatistics;
  private KeyValueStore<ScheduleKeyDTO, MessageScheduleDTO> store;
  private ProcessorContext<Object, SchedulableMessageDTO> context;
  private ScheduleProcessor scheduleProcessor;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    engineAuthorizationService = mock(EngineAuthorizationService.class);
    processingStatistics = mock(ProcessingStatistics.class);
    store = mock(KeyValueStore.class);
    context = mock(ProcessorContext.class);

    when(store.get(any())).thenAnswer(invocation -> storeMap.get(invocation.getArgument(0)));
    doAnswer(
            invocation -> {
              storeMap.put(invocation.getArgument(0), invocation.getArgument(1));
              return null;
            })
        .when(store)
        .put(any(), any());
    when(store.delete(any())).thenAnswer(invocation -> storeMap.remove(invocation.getArgument(0)));
    when(store.all()).thenReturn(emptyIterator());
    when(context.schedule(any(), eq(PunctuationType.WALL_CLOCK_TIME), any(Punctuator.class)))
        .thenReturn(mock(Cancellable.class));

    Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneId.systemDefault());
    scheduleProcessor =
        new ScheduleProcessor(
            clock,
            true,
            (ignored, unusedStoreName) -> store,
            new TimeBucket[] {TimeBucket.MINUTE},
            processingStatistics,
            engineAuthorizationService,
            SCHEDULE_TOPIC);
    scheduleProcessor.init(context);
  }

  @Test
  void authorizedEngineSchedule_isStoredAndMeasured() {
    DefinitionScheduleKeyDTO scheduleKey = scheduleKey();
    MessageScheduleDTO schedule = oneTimeSchedule();
    RecordHeaders headers = signedHeaders("engine-key-1");
    when(engineAuthorizationService.authorizeScheduleCommand(headers, scheduleKey, schedule))
        .thenReturn(activeKey("engine-key-1", KeyRole.ENGINE));

    scheduleProcessor.process(new Record<>(scheduleKey, schedule, 999_000L, headers));

    verify(store).put(scheduleKey, schedule);
    verify(processingStatistics).recordScheduleLatency(999_000L, "DefinitionScheduleKeyDTO_CREATE");
  }

  @Test
  void unauthorizedClientSchedule_isRejectedWithoutSideEffects() {
    DefinitionScheduleKeyDTO scheduleKey = scheduleKey();
    MessageScheduleDTO schedule = oneTimeSchedule();
    RecordHeaders headers = signedHeaders("client-key-1");
    when(engineAuthorizationService.authorizeScheduleCommand(headers, scheduleKey, schedule))
        .thenThrow(new AuthorizationTokenException("client signer not allowed"));

    scheduleProcessor.process(new Record<>(scheduleKey, schedule, 999_000L, headers));

    verify(store, never()).put(any(), any());
    verify(processingStatistics, never()).recordScheduleLatency(any(Long.class), any());
  }

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
    headers.add("X-TaktX-Signature", (keyId + ".AABB").getBytes(StandardCharsets.UTF_8));
    return headers;
  }

  private SigningKeyDTO activeKey(String keyId, KeyRole role) {
    return SigningKeyDTO.builder()
        .keyId(keyId)
        .publicKeyBase64("dummy")
        .algorithm("Ed25519")
        .owner("engine")
        .role(role)
        .build();
  }

  private KeyValueIterator<ScheduleKeyDTO, MessageScheduleDTO> emptyIterator() {
    return new KeyValueIterator<>() {
      @Override
      public void close() {
        // No-op for the empty in-memory iterator used by this unit test.
      }

      @Override
      public ScheduleKeyDTO peekNextKey() {
        return null;
      }

      @Override
      public boolean hasNext() {
        return false;
      }

      @Override
      public org.apache.kafka.streams.KeyValue<ScheduleKeyDTO, MessageScheduleDTO> next() {
        throw new java.util.NoSuchElementException();
      }
    };
  }
}
