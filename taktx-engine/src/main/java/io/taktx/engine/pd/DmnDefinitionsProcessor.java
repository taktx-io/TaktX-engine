/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd;

import io.taktx.dto.DmnDefinitionDTO;
import io.taktx.dto.DmnDefinitionKey;
import io.taktx.dto.DmnDefinitionStateEnum;
import io.taktx.dto.DmnDefinitionsDlqEntryDTO;
import io.taktx.dto.DmnDefinitionsKey;
import io.taktx.dto.ParsedDmnDefinitionsDTO;
import io.taktx.dto.XmlDmnDefinitionsDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.dmn.DmnDefinitionsCache;
import io.taktx.xml.DmnParser;
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
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class DmnDefinitionsProcessor
    implements Processor<String, XmlDmnDefinitionsDTO, Object, Object> {

  private static final String DLQ_REASON_HINT_HEADER = "X-TaktX-DLQ-Reason-Hint";
  private static final String DLQ_REASON_TEXT_HEADER = "X-TaktX-DLQ-Reason-Text";
  private static final String DLQ_CAPTURE_STAGE_HEADER = "X-TaktX-DLQ-Capture-Stage";

  private final TaktConfiguration taktConfiguration;
  private final Clock clock;
  private final DmnDefinitionsCache dmnDefinitionsCache;
  private ProcessorContext<Object, Object> context;
  private KeyValueStore<String, Map<String, Integer>> hashVersionStore;
  private final Map<String, Map<String, Integer>> hashVersionCache = new HashMap<>();

  public DmnDefinitionsProcessor(
      TaktConfiguration taktConfiguration, Clock clock, DmnDefinitionsCache dmnDefinitionsCache) {
    this.taktConfiguration = taktConfiguration;
    this.clock = clock;
    this.dmnDefinitionsCache = dmnDefinitionsCache;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    this.context = context;
    this.hashVersionStore =
        context.getStateStore(
            taktConfiguration.getPrefixed(Stores.DMN_VERSION_BY_HASH.getStorename()));
  }

  @Override
  public void process(Record<String, XmlDmnDefinitionsDTO> record) {
    if (record.value() == null || record.value().getXml() == null) {
      log.warn("⚠ Null payload on dmn-definitions for key {}, routing to DLQ", record.key());
      emitDmnDefinitionsDlq(
          record, "CBOR_DECODE_ERROR", "Null payload for dmn-definitions record", "PROCESSOR");
      return;
    }
    String dmnDefinitionId = record.key();
    String xml = record.value().getXml();
    log.info("Processing DMN definitions record for definition {}", dmnDefinitionId);

    try {
      ParsedDmnDefinitionsDTO parsed = DmnParser.parse(xml);
      DmnDefinitionsKey definitionsKey = parsed.getDefinitionsKey();

      Map<String, Integer> hashVersionPairs = getHashVersionPairs(dmnDefinitionId);
      if (hashVersionPairs == null) {
        hashVersionPairs = new HashMap<>();
      }

      Integer version = hashVersionPairs.get(definitionsKey.getHash());
      if (version == null) {
        version = hashVersionPairs.size() + 1;
        log.info("Creating new version {} of DMN definition {}", version, dmnDefinitionId);

        hashVersionPairs.put(definitionsKey.getHash(), version);
        hashVersionStore.put(dmnDefinitionId, hashVersionPairs);
        hashVersionCache.put(dmnDefinitionId, hashVersionPairs);

        DmnDefinitionKey key = new DmnDefinitionKey(dmnDefinitionId, version);
        DmnDefinitionDTO dto = new DmnDefinitionDTO(parsed, version, DmnDefinitionStateEnum.ACTIVE);

        dmnDefinitionsCache.put(key, dto);

        // Forward: (DmnDefinitionKey, DmnDefinitionDTO) → global-dmn-definition topic
        context.forward(new Record<>(key, dto, clock.millis()));
        // Forward: (DmnDefinitionKey, String xml) → xml-by-dmn-definition-id topic
        context.forward(new Record<>(key, xml, clock.millis()));
      } else {
        log.info(
            "Version {} of DMN definition {} already exists, no action needed",
            version,
            dmnDefinitionId);
      }
    } catch (Exception e) {
      log.error(
          "⚠ Exception processing dmn-definitions record for {}, routing to DLQ: {}",
          dmnDefinitionId,
          e.getMessage(),
          e);
      emitDmnDefinitionsDlq(record, "PROCESSOR_EXCEPTION", e.getMessage(), "PROCESSOR");
    }
  }

  private void emitDmnDefinitionsDlq(
      Record<String, XmlDmnDefinitionsDTO> record,
      String reasonHint,
      String reasonText,
      String captureStage) {
    Map<String, byte[]> headersMap = headersToMap(record.headers());
    headersMap.put(DLQ_REASON_HINT_HEADER, reasonHint.getBytes(StandardCharsets.UTF_8));
    headersMap.put(
        DLQ_REASON_TEXT_HEADER,
        (reasonText != null ? reasonText : "").getBytes(StandardCharsets.UTF_8));
    headersMap.put(DLQ_CAPTURE_STAGE_HEADER, captureStage.getBytes(StandardCharsets.UTF_8));
    DmnDefinitionsDlqEntryDTO dlqEntry =
        new DmnDefinitionsDlqEntryDTO(record.key(), record.value(), headersMap);
    context.forward(new Record<>(null, dlqEntry, clock.millis()));
  }

  private static Map<String, byte[]> headersToMap(org.apache.kafka.common.header.Headers headers) {
    if (headers == null) {
      return new HashMap<>();
    }
    return Arrays.stream(headers.toArray()).collect(Collectors.toMap(Header::key, Header::value));
  }

  private Map<String, Integer> getHashVersionPairs(String dmnDefinitionId) {
    Map<String, Integer> inMemory = hashVersionCache.get(dmnDefinitionId);
    if (inMemory != null) {
      return inMemory;
    }
    Map<String, Integer> fromStore = hashVersionStore.get(dmnDefinitionId);
    if (fromStore != null) {
      hashVersionCache.put(dmnDefinitionId, fromStore);
    }
    return fromStore;
  }
}
