/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.generic;

import io.micrometer.core.instrument.Metrics;
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
 * <p>A deserialization failure in Kafka Streams (e.g. a corrupt protobuf payload, a null/malformed
 * UUID key, or a signing-aware Serde rejection) would by default stop the affected stream thread
 * with the built-in {@code LogAndFailExceptionHandler}. This handler logs full details about the
 * offending record and tells Kafka Streams to skip it, keeping the engine alive.
 *
 * <p>Configure via {@code application.properties} using the raw Kafka Streams property key (the
 * {@code quarkus.kafka-streams.*} variant is silently ignored by Quarkus 3.35 / Kafka 4.x):
 *
 * <pre>
 * kafka-streams.default.deserialization.exception.handler=io.taktx.engine.generic.ContinueOnDeserializationErrorHandler
 * </pre>
 */
public class ContinueOnDeserializationErrorHandler implements DeserializationExceptionHandler {

  private static final Logger log =
      LoggerFactory.getLogger(ContinueOnDeserializationErrorHandler.class);

  @Override
  public void configure(Map<String, ?> configs) {
    // no configuration needed
  }

  /**
   * Kafka Streams 4.x non-deprecated entry point. Logs the poison record, increments a Micrometer
   * counter, and returns {@link Response#resume()} so the stream thread skips the record and
   * continues.
   */
  @Override
  public Response handleError(
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

    // DLQ-018A: increment observable counter so dashboards can detect excluded-topic poison
    // records. Guard against early-startup races where the global registry is not yet fully
    // initialised — a metric increment failure must never cause the handler to throw and
    // accidentally trigger SHUTDOWN_CLIENT behaviour.
    try {
      Metrics.globalRegistry
          .counter("taktx.excluded.topic.deserialization.errors", "topic", consumerRecord.topic())
          .increment();
    } catch (Exception metricEx) {
      log.warn(
          "Failed to increment deserialization-error metric (non-fatal): {}",
          metricEx.getMessage());
    }

    // resume() tells Kafka Streams to commit the offset and move past this record.
    // The stream thread stays alive; no state store is corrupted.
    return Response.resume();
  }
}
