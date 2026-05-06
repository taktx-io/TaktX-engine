/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.dlq;

import java.util.Map;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

/**
 * Thin KStream processor that converts a {@link DlqReplayForwardRecord} into a raw {@code
 * Record<String, byte[]>} suitable for production to a dynamic Kafka topic.
 *
 * <p>The output record key is set to the target topic's full prefixed name so that the downstream
 * {@link org.apache.kafka.streams.kstream.TopicNameExtractor} used in {@code
 * TopologyProducer.setupDlqReplayStream()} can route to the correct ingress topic without
 * additional state.
 *
 * <p>All headers carried in {@link DlqReplayForwardRecord#headers()} — including the lineage
 * triplet and the fresh engine signature — are propagated onto the outgoing Kafka record.
 */
public class DlqForwardingProcessor implements Processor<Object, Object, String, byte[]> {

  private ProcessorContext<String, byte[]> context;

  @Override
  public void init(ProcessorContext<String, byte[]> context) {
    this.context = context;
  }

  @Override
  public void process(Record<Object, Object> inputRecord) {
    if (!(inputRecord.value()
        instanceof
        DlqReplayForwardRecord(String targetTopic, byte[] payload, Map<String, byte[]> headers))) {
      return;
    }
    Headers recordHeaders = buildHeaders(headers);
    context.forward(new Record<>(targetTopic, payload, inputRecord.timestamp(), recordHeaders));
  }

  private static Headers buildHeaders(Map<String, byte[]> headersMap) {
    RecordHeaders headers = new RecordHeaders();
    if (headersMap != null) {
      headersMap.forEach(
          (k, v) -> {
            if (v != null) {
              headers.add(k, v);
            }
          });
    }
    return headers;
  }
}
