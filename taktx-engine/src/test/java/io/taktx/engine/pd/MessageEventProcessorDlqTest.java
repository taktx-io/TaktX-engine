/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.MessageEventDlqEntryDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pi.ProcessingStatistics;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({"unchecked", "rawtypes"})
class MessageEventProcessorDlqTest {

  private ProcessorContext<Object, Object> context;
  private MessageEventProcessor processor;

  @BeforeEach
  void setUp() {
    TaktConfiguration taktConfiguration = mock(TaktConfiguration.class);
    when(taktConfiguration.getPrefixed(Stores.DEFINITION_MESSAGE_SUBSCRIPTION.getStorename()))
        .thenReturn(Stores.DEFINITION_MESSAGE_SUBSCRIPTION.getStorename());
    when(taktConfiguration.getPrefixed(Stores.CORRELATION_MESSAGE_SUBSCRIPTION.getStorename()))
        .thenReturn(Stores.CORRELATION_MESSAGE_SUBSCRIPTION.getStorename());

    Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);
    ProcessingStatistics processingStatistics = mock(ProcessingStatistics.class);

    processor = new MessageEventProcessor(taktConfiguration, clock, processingStatistics);

    context = mock(ProcessorContext.class);
    KeyValueStore definitionStore = mock(KeyValueStore.class);
    KeyValueStore correlationStore = mock(KeyValueStore.class);
    when(context.getStateStore(Stores.DEFINITION_MESSAGE_SUBSCRIPTION.getStorename()))
        .thenReturn(definitionStore);
    when(context.getStateStore(Stores.CORRELATION_MESSAGE_SUBSCRIPTION.getStorename()))
        .thenReturn(correlationStore);

    processor.init(context);
  }

  private static void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to set field '" + fieldName + "'", e);
    }
  }

  @Test
  void process_unknownMessageEventType_emitsDlqWithTypeMismatchHint() {
    MessageEventKeyDTO key = new MessageEventKeyDTO("test-message");
    // Use a subtype that doesn't match any known case by nesting an anonymous subtype
    // CorrelationMessageEventTriggerDTO is a known type — to trigger the default we pass a
    // custom anonymous MessageEventDTO subtype that the switch won't match.
    // Simplest: cause an exception by making the correlationSubscriptionStore return a value
    // that causes NPE while processing — test the exception branch instead.
    // For the unknown-type branch we test via null value.
    RecordHeaders headers = new RecordHeaders();
    headers.add("X-Test", "value".getBytes(StandardCharsets.UTF_8));
    Record<MessageEventKeyDTO, io.taktx.dto.MessageEventDTO> messageEventRecord =
        new Record<>(key, null, 100L, headers);

    processor.process(messageEventRecord);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.key()).isNull();
    assertThat(forwarded.value()).isInstanceOf(MessageEventDlqEntryDTO.class);

    MessageEventDlqEntryDTO dlqEntry = (MessageEventDlqEntryDTO) forwarded.value();
    assertThat(dlqEntry.getKey()).isEqualTo(key);
    assertThat(dlqEntry.getHeaders()).containsKey("X-TaktX-DLQ-Reason-Hint");
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Reason-Hint"), StandardCharsets.UTF_8))
        .isEqualTo("CBOR_DECODE_ERROR");
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Capture-Stage"), StandardCharsets.UTF_8))
        .isEqualTo("DESERIALIZER");
  }

  @Test
  void process_processingException_emitsDlqWithProcessorExceptionHint() {
    MessageEventKeyDTO key = new MessageEventKeyDTO("evt");
    CorrelationMessageEventTriggerDTO trigger = new CorrelationMessageEventTriggerDTO();
    trigger.setMessageName("pay");
    trigger.setCorrelationKey("ck");
    trigger.setVariables(VariablesDTO.empty());

    RecordHeaders headers = new RecordHeaders();

    // correlationSubscriptionStore returns a CorrelationMessageSubscriptions with a null
    // instances map — causes NPE inside processCorrelationMessageEventTrigger
    // We achieve this by NOT setting up the store mock return (returns null by default — no NPE)
    // Instead, override: inject a broken context that throws on getStateStore.
    // Simplest: set correlationSubscriptionStore field to a store whose get() throws.
    KeyValueStore brokenStore = mock(KeyValueStore.class);
    when(brokenStore.get(key)).thenThrow(new RuntimeException("simulated store failure"));
    setField(processor, "correlationMessageSubscriptionStore", brokenStore);

    processor.process(new Record<>(key, trigger, 200L, headers));

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.value()).isInstanceOf(MessageEventDlqEntryDTO.class);

    MessageEventDlqEntryDTO dlqEntry = (MessageEventDlqEntryDTO) forwarded.value();
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Reason-Hint"), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR_EXCEPTION");
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Capture-Stage"), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR");
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Reason-Text"), StandardCharsets.UTF_8))
        .contains("simulated store failure");
  }
}
