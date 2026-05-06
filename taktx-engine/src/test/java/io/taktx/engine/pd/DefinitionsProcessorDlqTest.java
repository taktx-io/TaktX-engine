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

import io.taktx.dto.ProcessDefinitionDlqEntryDTO;
import io.taktx.dto.XmlDefinitionsDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pi.DefinitionsCache;
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
class DefinitionsProcessorDlqTest {

  private ProcessorContext<Object, Object> context;
  private DefinitionsProcessor processor;

  @BeforeEach
  void setUp() {
    TaktConfiguration taktConfiguration = mock(TaktConfiguration.class);
    when(taktConfiguration.getPrefixed(Stores.VERSION_BY_HASH.getStorename()))
        .thenReturn(Stores.VERSION_BY_HASH.getStorename());
    when(taktConfiguration.getPrefixed(Stores.GLOBAL_PROCESS_DEFINITION.getStorename()))
        .thenReturn(Stores.GLOBAL_PROCESS_DEFINITION.getStorename());

    Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);
    MessageSchedulerFactory schedulerFactory = mock(MessageSchedulerFactory.class);
    FeelExpressionHandler feelHandler = mock(FeelExpressionHandler.class);
    DefinitionsCache definitionsCache = mock(DefinitionsCache.class);

    processor =
        new DefinitionsProcessor(
            taktConfiguration, schedulerFactory, clock, feelHandler, definitionsCache);

    context = mock(ProcessorContext.class);
    KeyValueStore hashStore = mock(KeyValueStore.class);
    KeyValueStore defStore = mock(KeyValueStore.class);
    when(context.getStateStore(Stores.VERSION_BY_HASH.getStorename())).thenReturn(hashStore);
    when(context.getStateStore(Stores.GLOBAL_PROCESS_DEFINITION.getStorename()))
        .thenReturn(defStore);

    processor.init(context);
  }

  @Test
  void process_nullValue_emitsDlqWithDecodeErrorHint() {
    RecordHeaders headers = new RecordHeaders();
    headers.add("X-Source", "client".getBytes(StandardCharsets.UTF_8));
    Record<String, io.taktx.dto.DefinitionsTriggerDTO> record =
        new Record<>("proc-1", null, 100L, headers);

    processor.process(record);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.key()).isNull();
    assertThat(forwarded.value()).isInstanceOf(ProcessDefinitionDlqEntryDTO.class);

    ProcessDefinitionDlqEntryDTO dlqEntry = (ProcessDefinitionDlqEntryDTO) forwarded.value();
    assertThat(dlqEntry.getDefinitionsTrigger()).isNull();
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Reason-Hint"), StandardCharsets.UTF_8))
        .isEqualTo("CBOR_DECODE_ERROR");
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Capture-Stage"), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR");
  }

  @Test
  void process_xmlParseFails_emitsDlqWithProcessorExceptionHint() {
    // Inject a malformed XML that will cause BpmnParser.parse() to throw
    XmlDefinitionsDTO xmlDefinitions = new XmlDefinitionsDTO("NOT_VALID_XML <<<");
    RecordHeaders headers = new RecordHeaders();
    Record<String, io.taktx.dto.DefinitionsTriggerDTO> record =
        new Record<>("proc-2", xmlDefinitions, 200L, headers);

    processor.process(record);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.value()).isInstanceOf(ProcessDefinitionDlqEntryDTO.class);

    ProcessDefinitionDlqEntryDTO dlqEntry = (ProcessDefinitionDlqEntryDTO) forwarded.value();
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Reason-Hint"), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR_EXCEPTION");
    assertThat(
            new String(
                dlqEntry.getHeaders().get("X-TaktX-DLQ-Capture-Stage"), StandardCharsets.UTF_8))
        .isEqualTo("PROCESSOR");
  }
}
