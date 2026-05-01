/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd;

import io.taktx.dto.DefinitionsTriggerDTO;
import io.taktx.dto.DlqEntryDTO;
import io.taktx.dto.DlqEntryKey;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDefinitionActivationDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessDefinitionStateEnum;
import io.taktx.dto.XmlDefinitionsDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pi.DefinitionsCache;
import io.taktx.xml.BpmnParser;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

@Slf4j
public class DlqReplayProcessor
    implements Processor<DlqEntryKey, DlqEntryDTO, Object, Object> {

  private final TaktConfiguration taktConfiguration;
  private ProcessorContext<Object, Object> context;

  public DlqReplayProcessor(TaktConfiguration taktConfiguration) {
    this.taktConfiguration = taktConfiguration;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
  }

  @Override
  public void process(Record<DlqEntryKey, DlqEntryDTO> dlqRecord) {
  }

}
