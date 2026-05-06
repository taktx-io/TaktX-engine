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

import io.taktx.dto.DmnDefinitionsDlqEntryDTO;
import io.taktx.dto.XmlDmnDefinitionsDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.dmn.DmnDefinitionsCache;
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
class DmnDefinitionsProcessorDlqTest {

  private ProcessorContext<Object, Object> context;
  private DmnDefinitionsProcessor processor;

  @BeforeEach
  void setUp() {
    TaktConfiguration taktConfiguration = mock(TaktConfiguration.class);
    when(taktConfiguration.getPrefixed(Stores.DMN_VERSION_BY_HASH.getStorename()))
        .thenReturn(Stores.DMN_VERSION_BY_HASH.getStorename());

    Clock clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);
    DmnDefinitionsCache cache = mock(DmnDefinitionsCache.class);

    processor = new DmnDefinitionsProcessor(taktConfiguration, clock, cache);

    context = mock(ProcessorContext.class);
    KeyValueStore hashStore = mock(KeyValueStore.class);
    when(context.getStateStore(Stores.DMN_VERSION_BY_HASH.getStorename())).thenReturn(hashStore);

    processor.init(context);
  }

  @Test
  void process_nullValue_emitsDlqWithDecodeErrorHint() {
    RecordHeaders headers = new RecordHeaders();
    headers.add("X-Origin", "dmn-client".getBytes(StandardCharsets.UTF_8));
    Record<String, XmlDmnDefinitionsDTO> definitionsRecord =
        new Record<>("dmn-1", null, 100L, headers);

    processor.process(definitionsRecord);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.key()).isNull();
    assertThat(forwarded.value()).isInstanceOf(DmnDefinitionsDlqEntryDTO.class);

    DmnDefinitionsDlqEntryDTO dlqEntry = (DmnDefinitionsDlqEntryDTO) forwarded.value();
    assertThat(dlqEntry.getDmnDefinitionId()).isEqualTo("dmn-1");
    assertThat(dlqEntry.getValue()).isNull();
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
  void process_invalidXml_emitsDlqWithProcessorExceptionHint() {
    XmlDmnDefinitionsDTO dto = new XmlDmnDefinitionsDTO("NOT_VALID_DMN_XML <<<");
    RecordHeaders headers = new RecordHeaders();
    Record<String, XmlDmnDefinitionsDTO> definitionsRecord =
        new Record<>("dmn-2", dto, 200L, headers);

    processor.process(definitionsRecord);

    ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
    verify(context).forward(captor.capture());
    Record forwarded = captor.getValue();
    assertThat(forwarded.value()).isInstanceOf(DmnDefinitionsDlqEntryDTO.class);

    DmnDefinitionsDlqEntryDTO dlqEntry = (DmnDefinitionsDlqEntryDTO) forwarded.value();
    assertThat(dlqEntry.getDmnDefinitionId()).isEqualTo("dmn-2");
    assertThat(dlqEntry.getValue()).isSameAs(dto);
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
