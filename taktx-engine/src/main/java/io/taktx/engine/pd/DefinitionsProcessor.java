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

import io.taktx.dto.DefinitionsTriggerDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDefinitionActivationDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionDlqEntryDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ProcessDefinitionStateEnum;
import io.taktx.dto.XmlDefinitionsDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pi.DefinitionsCache;
import io.taktx.xml.BpmnParser;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

@Slf4j
public class DefinitionsProcessor
    implements Processor<String, DefinitionsTriggerDTO, Object, Object> {
  private final TaktConfiguration taktConfiguration;
  private final MessageSchedulerFactory messageSchedulerFactory;
  private ProcessorContext<Object, Object> context;
  private KeyValueStore<String, Map<String, Integer>> hashVersionPairStore;
  private KeyValueStore<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>>
      processDefinitionStore;
  private final Map<String, Map<String, Integer>> hashVersionPairCache = new HashMap<>();
  private ProcessDefinitionActivationProcessor processDefinitionActivationProcessor;
  private final Clock clock;
  private final FeelExpressionHandler feelExpressionHandler;
  private final DefinitionsCache definitionsCache;

  public DefinitionsProcessor(
      TaktConfiguration taktConfiguration,
      MessageSchedulerFactory messageSchedulerFactory,
      Clock clock,
      FeelExpressionHandler feelExpressionHandler,
      DefinitionsCache definitionsCache) {
    this.taktConfiguration = taktConfiguration;
    this.messageSchedulerFactory = messageSchedulerFactory;
    this.clock = clock;
    this.feelExpressionHandler = feelExpressionHandler;
    this.definitionsCache = definitionsCache;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.hashVersionPairStore =
        context.getStateStore(taktConfiguration.getPrefixed(Stores.VERSION_BY_HASH.getStorename()));
    this.processDefinitionStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.GLOBAL_PROCESS_DEFINITION.getStorename()));
    processDefinitionActivationProcessor =
        new ProcessDefinitionActivationProcessor(
            taktConfiguration,
            messageSchedulerFactory,
            context,
            clock,
            feelExpressionHandler,
            definitionsCache);
  }

  @Override
  public void process(Record<String, DefinitionsTriggerDTO> definitionsRecord) {
    if (definitionsRecord.value() == null) {
      emitDefinitionsDlq(
          definitionsRecord,
          "CBOR_DECODE_ERROR",
          "Null payload for definitions record",
          "DESERIALIZER");
      return;
    }
    try {
      if (definitionsRecord.value() instanceof XmlDefinitionsDTO xmlDefinitions) {
        processDefinitionsRecord(definitionsRecord.key(), xmlDefinitions);
      } else if (definitionsRecord.value()
          instanceof ProcessDefinitionActivationDTO processDefinitionActivationDTO) {
        processDefinitionActivationProcessor.process(processDefinitionActivationDTO);
      } else {
        log.warn(
            "⚠ Unknown definitions trigger type, routing to DLQ: {}",
            definitionsRecord.value().getClass().getName());
        emitDefinitionsDlq(
            definitionsRecord,
            "CBOR_TYPE_MISMATCH",
            "Unknown definitions trigger type: " + definitionsRecord.value().getClass().getName(),
            "PROCESSOR");
      }
    } catch (Exception e) {
      log.error("⚠ Exception processing definitions record, routing to DLQ: {}", e.getMessage(), e);
      emitDefinitionsDlq(definitionsRecord, "PROCESSOR_EXCEPTION", e.getMessage(), "PROCESSOR");
    }
  }

  private void emitDefinitionsDlq(
      Record<String, DefinitionsTriggerDTO> definitionsRecord,
      String reasonHint,
      String reasonText,
      String captureStage) {
    Map<String, byte[]> headersMap = headersToMap(definitionsRecord.headers());
    headersMap.put(REASON_HINT, reasonHint.getBytes(StandardCharsets.UTF_8));
    headersMap.put(
        REASON_TEXT, (reasonText != null ? reasonText : "").getBytes(StandardCharsets.UTF_8));
    headersMap.put(CAPTURE_STAGE, captureStage.getBytes(StandardCharsets.UTF_8));
    ProcessDefinitionDlqEntryDTO dlqEntry =
        new ProcessDefinitionDlqEntryDTO(null, definitionsRecord.value(), headersMap);
    context.forward(new Record<>(null, dlqEntry, clock.millis()));
  }

  private static Map<String, byte[]> headersToMap(org.apache.kafka.common.header.Headers headers) {
    if (headers == null) {
      return new HashMap<>();
    }
    return Arrays.stream(headers.toArray()).collect(Collectors.toMap(Header::key, Header::value));
  }

  public void processDefinitionsRecord(
      String processDefinitionId, XmlDefinitionsDTO xmlDefinitions) {
    log.info("Processing definitions record for process definition {}", processDefinitionId);
    ParsedDefinitionsDTO parsedDefinition = BpmnParser.parse(xmlDefinitions.getXml());

    Map<String, Integer> hashVersionPairs =
        getHashVersionPairs(parsedDefinition.getDefinitionsKey().getProcessDefinitionId());
    if (hashVersionPairs == null) {
      hashVersionPairs = new HashMap<>();
    }
    Integer version = hashVersionPairs.get(parsedDefinition.getDefinitionsKey().getHash());

    ProcessDefinitionDTO processDefinitionDTO;
    if (version == null) {
      version = hashVersionPairs.size() + 1;
      log.info("Creating new version {} of process definition {}", version, processDefinitionId);

      // New version, create a new ProcessDefinitionDTO and store the relevant information
      String hash = parsedDefinition.getDefinitionsKey().getHash();
      hashVersionPairs.put(hash, version);

      hashVersionPairStore.put(processDefinitionId, hashVersionPairs);
      hashVersionPairCache.put(processDefinitionId, hashVersionPairs);
      ProcessDefinitionKey processDefinitionKey =
          new ProcessDefinitionKey(processDefinitionId, version);
      context.forward(new Record<>(processDefinitionKey, xmlDefinitions.getXml(), clock.millis()));

      processDefinitionDTO =
          new ProcessDefinitionDTO(parsedDefinition, version, ProcessDefinitionStateEnum.ACTIVE);
      processDefinitionActivationProcessor.activate(processDefinitionDTO);
    } else {
      // Existing version, do not create a new ProcessDefinitionDTO but return the active version
      log.info("Version {} of process definition {} already exists", version, processDefinitionId);
      ProcessDefinitionKey startKey = new ProcessDefinitionKey(processDefinitionId, 1);
      ProcessDefinitionKey endKey =
          new ProcessDefinitionKey(processDefinitionId, Integer.MAX_VALUE);
      try (KeyValueIterator<ProcessDefinitionKey, ValueAndTimestamp<ProcessDefinitionDTO>> range =
          processDefinitionStore.range(startKey, endKey)) {
        range.forEachRemaining(
            entry -> {
              log.info(
                  "Checking version {} of process definition {}",
                  entry.key.getVersion(),
                  processDefinitionId);
              if (entry.value.value().getState() == ProcessDefinitionStateEnum.ACTIVE) {
                log.info(
                    "Found active version {} of process definition {}",
                    entry.key.getVersion(),
                    processDefinitionId);
                context.forward(new Record<>(entry.key, entry.value.value(), clock.millis()));
              }
            });
      }
    }
  }

  private Map<String, Integer> getHashVersionPairs(String processDefinitionId) {
    Map<String, Integer> stringIntegerMap = hashVersionPairCache.get(processDefinitionId);
    if (stringIntegerMap != null) {
      return stringIntegerMap;
    } else {
      Map<String, Integer> stringIntegerMap1 = hashVersionPairStore.get(processDefinitionId);
      if (stringIntegerMap1 != null) {
        hashVersionPairCache.put(processDefinitionId, stringIntegerMap1);
      }
      return stringIntegerMap1;
    }
  }
}
