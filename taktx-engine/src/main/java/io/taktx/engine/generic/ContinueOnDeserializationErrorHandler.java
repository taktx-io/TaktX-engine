/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.generic;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.ErrorHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka Streams deserialization exception handler that logs the poison record and continues
 * processing, rather than stopping the stream thread.
 *
 * <p>A deserialization failure in Kafka Streams (e.g. a corrupt CBOR payload, or a signature
 * verification failure in a signing-aware Serde) would by default stop the affected stream thread
 * with {@code LogAndFailExceptionHandler}. This handler logs full details about the offending
 * record and tells Kafka Streams to skip it, keeping the engine alive.
 *
 * <p>Configure via {@code application.properties}:
 *
 * <pre>
 * quarkus.kafka-streams.default-deserialization-exception-handler=io.taktx.engine.generic.ContinueOnDeserializationErrorHandler
 * </pre>
 */
public class ContinueOnDeserializationErrorHandler implements DeserializationExceptionHandler {

  private static final Logger log =
      LoggerFactory.getLogger(ContinueOnDeserializationErrorHandler.class);

  @Override
  public void configure(Map<String, ?> configs) {
    // no configuration needed
  }

  @Override
  public DeserializationHandlerResponse handle(
      ErrorHandlerContext context,
      ConsumerRecord<byte[], byte[]> consumerRecord,
      Exception exception) {

    log.error(
        "Deserialization error in Kafka Streams — skipping poison record."
            + " topic={} partition={} offset={} keyBytes={} valueBytes={} cause={}",
        consumerRecord.topic(),
        consumerRecord.partition(),
        consumerRecord.offset(),
        consumerRecord.key() != null ? consumerRecord.key().length : -1,
        consumerRecord.value() != null ? consumerRecord.value().length : -1,
        exception.getMessage(),
        exception);

    // CONTINUE tells Kafka Streams to commit the offset and move past this record.
    // The stream thread stays alive; no state store is corrupted.
    return DeserializationHandlerResponse.CONTINUE;
  }
}
