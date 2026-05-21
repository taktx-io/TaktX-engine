/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pd;

import static io.taktx.engine.dlq.DlqHeaders.CAPTURE_STAGE;
import static io.taktx.engine.dlq.DlqHeaders.REASON_HINT;
import static io.taktx.engine.dlq.DlqHeaders.REASON_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.SignalDTO;
import io.taktx.dto.SignalDlqEntryDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.SignalDefinitionSubscriptionKeyDTO;
import io.taktx.engine.generic.SignalInstanceSubscriptionKeyDTO;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({"unchecked", "rawtypes"})
class SignalProcessorDlqTest {

  private ProcessorContext<Object, Object> context;
  private SignalProcessor processor;
  private KeyValueStore<SignalInstanceSubscriptionKeyDTO, String> instanceStore;
  private KeyValueStore<SignalDefinitionSubscriptionKeyDTO, String> definitionStore;

  @BeforeEach
  void setUp() {
    TaktConfiguration taktConfiguration = mock(TaktConfiguration.class);
    when(taktConfiguration.getPrefixed(Stores.INSTANCE_SIGNAL_SUBSCRIPTIONS.getStorename()))
        .thenReturn(Stores.INSTANCE_SIGNAL_SUBSCRIPTIONS.getStorename());
    when(taktConfiguration.getPrefixed(Stores.DEFINITION_SIGNAL_SUBSCRIPTIONS.getStorename()))
        .thenReturn(Stores.DEFINITION_SIGNAL_SUBSCRIPTIONS.getStorename());

    Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);
    processor = new SignalProcessor(taktConfiguration, clock);

    context = mock(ProcessorContext.class);
    instanceStore = mock(KeyValueStore.class);
    definitionStore = mock(KeyValueStore.class);

    when(context.getStateStore(Stores.INSTANCE_SIGNAL_SUBSCRIPTIONS.getStorename()))
        .thenReturn(instanceStore);
    when(context.getStateStore(Stores.DEFINITION_SIGNAL_SUBSCRIPTIONS.getStorename()))
        .thenReturn(definitionStore);

    processor.init(context);
  }

  @Test
  void process_nullValue_emitsDlqWithDecodeErrorHint() {
    RecordHeaders headers = new RecordHeaders();
    headers.add("X-Origin", "client".getBytes(StandardCharsets.UTF_8));
    Record<String, SignalDTO> signalRecord = new Record<>("signal-key", null, 100L, headers);

    processor.process(signalRecord);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.key()).isNull();
    assertThat(forwarded.value()).isInstanceOf(SignalDlqEntryDTO.class);

    SignalDlqEntryDTO dlqEntry = (SignalDlqEntryDTO) forwarded.value();
    assertThat(dlqEntry.getSignalKey()).isEqualTo("signal-key");
    assertThat(dlqEntry.getValue()).isNull();
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("PAYLOAD_DESERIALIZATION_ERROR");
    assertThat(new String(dlqEntry.getHeaders().get(CAPTURE_STAGE), StandardCharsets.UTF_8))
        .isEqualTo("DESERIALIZER");
  }

  @Test
  void process_storeThrowsException_emitsDlqWithProcessorExceptionHint() {
    // Make definitionStore.range() return an empty iterator so handleDefinitionSignals succeeds.
    // Then instanceStore.range() throws, triggering the DLQ path.
    @SuppressWarnings("unchecked")
    KeyValueIterator<SignalDefinitionSubscriptionKeyDTO, String> emptyDefIt =
        mock(KeyValueIterator.class);
    when(emptyDefIt.hasNext()).thenReturn(false);
    when(definitionStore.range(any(), any())).thenReturn(emptyDefIt);
    when(instanceStore.range(any(), any())).thenThrow(new RuntimeException("store unavailable"));

    SignalDTO signal = new SignalDTO("order-placed");
    RecordHeaders headers = new RecordHeaders();
    Record<String, SignalDTO> signalRecord = new Record<>("order-placed", signal, 200L, headers);

    processor.process(signalRecord);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.value()).isInstanceOf(SignalDlqEntryDTO.class);

    SignalDlqEntryDTO dlqEntry = (SignalDlqEntryDTO) forwarded.value();
    assertThat(dlqEntry.getSignalKey()).isEqualTo("order-placed");
    assertThat(dlqEntry.getValue()).isSameAs(signal);
    assertThat(new String(dlqEntry.getHeaders().get(REASON_HINT), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR_EXCEPTION");
    assertThat(new String(dlqEntry.getHeaders().get(REASON_TEXT), StandardCharsets.UTF_8))
        .contains("store unavailable");
  }
}
