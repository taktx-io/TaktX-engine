/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.SecurityEventDTO;
import io.taktx.proto.SecurityEventMessage;
import io.taktx.serdes.SecurityEventProtoMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Watches the append-only security-events topic and keeps a bounded recent event history. */
final class SecurityEventTopicStore implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(SecurityEventTopicStore.class);

  private final KafkaConsumer<String, byte[]> consumer;
  private final String topic;
  private final ClientSecurityEventStore store;
  private final Consumer<SecurityEventDTO> onSecurityEvent;
  private final int replayLimitPerPartition;
  private final AtomicBoolean ready = new AtomicBoolean(false);
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "security-event-store-poll");
            thread.setDaemon(true);
            return thread;
          });

  SecurityEventTopicStore(
      Properties consumerProperties,
      String topic,
      ClientSecurityEventStore store,
      Consumer<SecurityEventDTO> onSecurityEvent,
      int replayLimitPerPartition) {
    this.topic = topic;
    this.store = store;
    this.onSecurityEvent = onSecurityEvent != null ? onSecurityEvent : event -> {};
    if (replayLimitPerPartition <= 0) {
      throw new IllegalArgumentException("replayLimitPerPartition must be > 0");
    }
    this.replayLimitPerPartition = replayLimitPerPartition;

    Properties props = new Properties();
    props.putAll(consumerProperties);
    props.put(
        ConsumerConfig.GROUP_ID_CONFIG, "security-event-store-" + ProcessHandle.current().pid());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

    this.consumer = new KafkaConsumer<>(props);
    initialLoad();
    startBackgroundPoll();
  }

  void awaitReady(Duration timeout) {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    while (!ready.get()) {
      if (System.currentTimeMillis() > deadline) {
        throw new IllegalStateException(
            "SecurityEventTopicStore did not become ready within " + timeout);
      }
      if (Thread.currentThread().isInterrupted()) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for SecurityEventTopicStore");
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        log.warn(
            "SecurityEventTopicStore: scheduler did not terminate within 5 s — proceeding with close");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("SecurityEventTopicStore: interrupted while waiting for scheduler shutdown");
    }
    consumer.close();
  }

  private void initialLoad() {
    try {
      List<TopicPartition> topicPartitions =
          consumer.partitionsFor(topic).stream()
              .map(info -> new TopicPartition(info.topic(), info.partition()))
              .toList();
      if (topicPartitions.isEmpty()) {
        log.warn(
            "SecurityEventTopicStore: topic '{}' has no partitions yet — recent event history will remain empty",
            topic);
        ready.set(true);
        return;
      }

      consumer.assign(topicPartitions);
      Map<TopicPartition, Long> endOffsets = consumer.endOffsets(topicPartitions);
      for (TopicPartition topicPartition : topicPartitions) {
        long endOffset = endOffsets.getOrDefault(topicPartition, 0L);
        long startOffset = Math.max(0L, endOffset - replayLimitPerPartition);
        consumer.seek(topicPartition, startOffset);
      }
      Map<TopicPartition, Long> remaining = new java.util.HashMap<>();
      for (Map.Entry<TopicPartition, Long> entry : endOffsets.entrySet()) {
        long startOffset = Math.max(0L, entry.getValue() - replayLimitPerPartition);
        if (entry.getValue() > startOffset) {
          remaining.put(entry.getKey(), entry.getValue());
        }
      }
      while (!remaining.isEmpty()) {
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, byte[]> consumerRecord : records) {
          applyRecord(consumerRecord);
          TopicPartition tp =
              new TopicPartition(consumerRecord.topic(), consumerRecord.partition());
          if (consumerRecord.offset() + 1 >= remaining.getOrDefault(tp, 0L)) {
            remaining.remove(tp);
          }
        }
      }
      ready.set(true);
      log.info(
          "SecurityEventTopicStore: initial load complete — recentEventCount={}",
          store.snapshot().size());
    } catch (Exception e) {
      log.warn(
          "SecurityEventTopicStore: initial load failed — recent event history will remain empty: {}",
          e.getMessage());
      ready.set(true);
    }
  }

  private void startBackgroundPoll() {
    scheduler.scheduleWithFixedDelay(
        () -> {
          try {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, byte[]> consumerRecord : records) {
              applyRecord(consumerRecord);
            }
          } catch (Exception e) {
            if (!scheduler.isShutdown()) {
              log.warn("SecurityEventTopicStore background poll error: {}", e.getMessage());
            }
          }
        },
        1,
        1,
        TimeUnit.SECONDS);
  }

  private void applyRecord(ConsumerRecord<String, byte[]> consumerRecord) {
    if (consumerRecord.value() == null) {
      return;
    }
    try {
      SecurityEventDTO event =
          SecurityEventProtoMapper.toDto(SecurityEventMessage.parseFrom(consumerRecord.value()));
      store.append(event);
      notifySecurityEvent(event);
    } catch (Exception e) {
      log.warn(
          "SecurityEventTopicStore: failed to deserialize security event record key={}: {}",
          consumerRecord.key(),
          e.getMessage());
    }
  }

  private void notifySecurityEvent(SecurityEventDTO event) {
    try {
      onSecurityEvent.accept(event);
    } catch (Exception e) {
      log.warn("SecurityEventTopicStore: security-event callback failed: {}", e.getMessage());
    }
  }
}
