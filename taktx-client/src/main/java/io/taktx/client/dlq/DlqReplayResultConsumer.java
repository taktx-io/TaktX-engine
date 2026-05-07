/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.dlq;

import io.taktx.Topics;
import io.taktx.dto.DlqReplayResult;
import io.taktx.util.TaktPropertiesHelper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes {@link DlqReplayResult} records from the {@code dlq.replay-results} topic.
 *
 * <p>Each result corresponds to a {@link io.taktx.dto.DlqReplayCommand} previously submitted via
 * {@link DlqReplayCommandProducer}. Use {@link DlqReplayResult#getCorrectionId()} to correlate the
 * result with the originating command, and {@link DlqReplayResult#getStatus()} to determine whether
 * the replay succeeded, failed, or was a dry-run pass.
 *
 * <p>This is a <em>Community</em>-tier feature.
 */
public class DlqReplayResultConsumer {

  private static final Logger log = LoggerFactory.getLogger(DlqReplayResultConsumer.class);

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;

  private volatile boolean running = false;
  private volatile KafkaConsumer<String, DlqReplayResult> activeConsumer;
  private final List<Consumer<DlqReplayResult>> handlers = new ArrayList<>();

  /**
   * Creates a new {@code DlqReplayResultConsumer}.
   *
   * @param taktPropertiesHelper helper carrying Kafka bootstrap and namespace configuration
   * @param executor executor used for the background polling loop
   */
  public DlqReplayResultConsumer(TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  /**
   * Registers a handler for replay results, resuming from the last committed offset.
   *
   * @param groupId Kafka consumer group ID
   * @param handler callback invoked for each replay result (called on the polling thread)
   */
  public void registerConsumer(String groupId, Consumer<DlqReplayResult> handler) {
    if (handlers.isEmpty()) {
      startPolling(groupId);
    }
    handlers.add(handler);
  }

  /** Stops the consumer; the background loop exits cleanly on the next poll cycle. */
  public void stop() {
    running = false;
    KafkaConsumer<String, DlqReplayResult> c = activeConsumer;
    if (c != null) {
      c.wakeup();
    }
  }

  // ── Internals ────────────────────────────────────────────────────────────────

  private void startPolling(String groupId) {
    running = true;
    CompletableFuture.runAsync(
        () -> {
          String topicName =
              taktPropertiesHelper.getPrefixedTopicName(Topics.DLQ_REPLAY_RESULTS.getTopicName());
          try (KafkaConsumer<String, DlqReplayResult> consumer = createConsumer(groupId)) {
            activeConsumer = consumer;
            consumer.subscribe(Collections.singletonList(topicName));

            log.info("DlqReplayResultConsumer started: groupId={} topic={}", groupId, topicName);
            try {
              while (running) {
                poll(consumer);
              }
            } catch (org.apache.kafka.common.errors.WakeupException e) {
              // stop() was called — exit cleanly
            } finally {
              activeConsumer = null;
              consumer.unsubscribe();
              log.info("DlqReplayResultConsumer stopped: groupId={}", groupId);
            }
          }
        },
        executor);
  }

  private void poll(KafkaConsumer<String, DlqReplayResult> consumer) {
    ConsumerRecords<String, DlqReplayResult> records;
    try {
      records = consumer.poll(Duration.ofMillis(100));
    } catch (org.apache.kafka.common.errors.RecordDeserializationException e) {
      log.error(
          "DlqReplayResultConsumer: failed to deserialise DlqReplayResult topic={} partition={}"
              + " offset={} — seeking past poison record: {}",
          e.topicPartition().topic(),
          e.topicPartition().partition(),
          e.offset(),
          e.getMessage());
      consumer.seek(e.topicPartition(), e.offset() + 1);
      return;
    }

    for (ConsumerRecord<String, DlqReplayResult> rec : records) {
      DlqReplayResult result = rec.value();
      if (result == null) {
        log.warn(
            "DlqReplayResultConsumer: null DlqReplayResult at topic={} partition={} offset={}"
                + " — skipping",
            rec.topic(),
            rec.partition(),
            rec.offset());
        continue;
      }
      for (Consumer<DlqReplayResult> h : handlers) {
        try {
          h.accept(result);
        } catch (Exception ex) {
          log.error(
              "DlqReplayResultConsumer handler threw exception for dlqEntryRef={} correctionId={}:"
                  + " {}",
              result.getDlqEntryRef(),
              result.getCorrectionId(),
              ex.getMessage(),
              ex);
        }
      }
    }
  }

  private KafkaConsumer<String, DlqReplayResult> createConsumer(String groupId) {
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId, StringDeserializer.class, DlqReplayResultJsonDeserializer.class, "latest");
    return new KafkaConsumer<>(props);
  }
}
