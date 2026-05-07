/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.dlq;

import io.taktx.Topics;
import io.taktx.dto.DlqEnvelope;
import io.taktx.dto.DlqSeverity;
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
 * Consumes {@link DlqEnvelope} records from the unified {@code dlq} topic.
 *
 * <p>Register one or more handler callbacks via {@link #registerConsumer}. The first registration
 * starts a background polling loop (virtual thread). All subsequent registrations add callbacks to
 * the same running consumer.
 *
 * <p>Offset management follows standard Kafka auto-commit semantics (the default consumer group
 * behaviour). For a replay-from-beginning strategy, pass {@code auto.offset.reset=earliest} or
 * {@code startFromEarliest=true} to {@link #registerConsumer}.
 *
 * <p>This is a <em>Community</em>-tier feature — any application with access to the Kafka cluster
 * and the correct topic ACL can use this class directly without a Premium console subscription.
 */
public class DlqEntryConsumer {

  private static final Logger log = LoggerFactory.getLogger(DlqEntryConsumer.class);

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;

  private volatile boolean running = false;
  private volatile KafkaConsumer<String, DlqEnvelope> activeConsumer;
  private final List<Consumer<DlqEnvelope>> handlers = new ArrayList<>();

  /**
   * Creates a new {@code DlqEntryConsumer}.
   *
   * @param taktPropertiesHelper helper carrying Kafka bootstrap and namespace configuration
   * @param executor executor used for the background polling loop (typically a virtual-thread
   *     executor)
   */
  public DlqEntryConsumer(TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  /**
   * Registers a handler that receives every {@link DlqEnvelope} from the {@code dlq} topic,
   * resuming from the last committed offset for this consumer group.
   *
   * <p>The first call starts the background polling loop. Additional handlers registered later are
   * added to the same loop.
   *
   * @param groupId Kafka consumer group ID (determines offset tracking)
   * @param handler callback invoked for each envelope record (called on the polling thread)
   */
  public void registerConsumer(String groupId, Consumer<DlqEnvelope> handler) {
    registerConsumer(groupId, handler, false);
  }

  /**
   * Registers a handler with explicit start-from-beginning control.
   *
   * @param groupId Kafka consumer group ID
   * @param handler callback invoked for each envelope record
   * @param startFromEarliest when {@code true}, seeks every assigned partition to offset 0 after
   *     the first rebalance, guaranteeing a full-history replay regardless of committed offsets
   */
  public void registerConsumer(
      String groupId, Consumer<DlqEnvelope> handler, boolean startFromEarliest) {
    if (handlers.isEmpty()) {
      startPolling(groupId, startFromEarliest);
    }
    handlers.add(handler);
  }

  /** Stops the consumer; the background loop exits cleanly on the next poll cycle. */
  public void stop() {
    running = false;
    KafkaConsumer<String, DlqEnvelope> c = activeConsumer;
    if (c != null) {
      c.wakeup();
    }
  }

  // ── Internals ────────────────────────────────────────────────────────────────

  private void startPolling(String groupId, boolean startFromEarliest) {
    running = true;
    CompletableFuture.runAsync(
        () -> {
          String topicName = taktPropertiesHelper.getPrefixedTopicName(Topics.DLQ.getTopicName());
          try (KafkaConsumer<String, DlqEnvelope> consumer = createConsumer(groupId)) {
            activeConsumer = consumer;
            consumer.subscribe(Collections.singletonList(topicName));

            if (startFromEarliest) {
              // Wait for partition assignment then seek to the beginning.
              while (consumer.assignment().isEmpty()) {
                consumer.poll(Duration.ofMillis(100));
              }
              consumer.seekToBeginning(consumer.assignment());
              log.info(
                  "DlqEntryConsumer seeked to beginning: groupId={} topic={} partitions={}",
                  groupId,
                  topicName,
                  consumer.assignment());
            }

            log.info("DlqEntryConsumer started: groupId={} topic={}", groupId, topicName);
            try {
              while (running) {
                poll(consumer);
              }
            } catch (org.apache.kafka.common.errors.WakeupException e) {
              // stop() was called — exit cleanly
            } finally {
              activeConsumer = null;
              consumer.unsubscribe();
              log.info("DlqEntryConsumer stopped: groupId={}", groupId);
            }
          }
        },
        executor);
  }

  private void poll(KafkaConsumer<String, DlqEnvelope> consumer) {
    ConsumerRecords<String, DlqEnvelope> records;
    try {
      records = consumer.poll(Duration.ofMillis(100));
    } catch (org.apache.kafka.common.errors.RecordDeserializationException e) {
      log.error(
          "DlqEntryConsumer: failed to deserialise DlqEnvelope topic={} partition={} offset={}"
              + " — seeking past poison record: {}",
          e.topicPartition().topic(),
          e.topicPartition().partition(),
          e.offset(),
          e.getMessage());
      consumer.seek(e.topicPartition(), e.offset() + 1);
      return;
    }

    for (ConsumerRecord<String, DlqEnvelope> rec : records) {
      DlqEnvelope envelope = rec.value();
      if (envelope == null) {
        log.warn(
            "DlqEntryConsumer: null DlqEnvelope at topic={} partition={} offset={} — skipping",
            rec.topic(),
            rec.partition(),
            rec.offset());
        continue;
      }
      // Populate Kafka-level coordinates into the envelope if missing (e.g. captured before
      // partition/offset were available at the processor level).
      if (envelope.getSourcePartition() == null) {
        envelope.setSourcePartition(rec.partition());
      }
      if (envelope.getSourceOffset() == null) {
        envelope.setSourceOffset(rec.offset());
      }
      if (envelope.getSeverity() == null) {
        envelope.setSeverity(
            envelope.getReasonCode() != null
                ? envelope.getReasonCode().getSeverity()
                : DlqSeverity.LOW);
      }
      for (Consumer<DlqEnvelope> h : handlers) {
        try {
          h.accept(envelope);
        } catch (Exception ex) {
          log.error(
              "DlqEntryConsumer handler threw exception for DLQ entry sourceTopic={}"
                  + " sourceOffset={}: {}",
              envelope.getSourceTopic(),
              envelope.getSourceOffset(),
              ex.getMessage(),
              ex);
        }
      }
    }
  }

  private KafkaConsumer<String, DlqEnvelope> createConsumer(String groupId) {
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId, StringDeserializer.class, DlqEnvelopeJsonDeserializer.class, "latest");
    return new KafkaConsumer<>(props);
  }
}
