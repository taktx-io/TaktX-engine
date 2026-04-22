/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.security.RuntimeConfigurationHolder;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches the compacted {@code taktx-configuration} topic and keeps {@link
 * RuntimeConfigurationHolder} up to date for client-side runtime decisions.
 */
public final class RuntimeConfigurationStore implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(RuntimeConfigurationStore.class);
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final String CONFIG_KEY = "config";

  private final KafkaConsumer<String, byte[]> consumer;
  private final String topic;
  private final Runnable onRuntimeConfigurationChanged;
  private final AtomicBoolean ready = new AtomicBoolean(false);
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "runtime-configuration-store-poll");
            thread.setDaemon(true);
            return thread;
          });

  RuntimeConfigurationStore(
      Properties consumerProperties, String topic, Runnable onRuntimeConfigurationChanged) {
    this.topic = topic;
    this.onRuntimeConfigurationChanged =
        onRuntimeConfigurationChanged != null ? onRuntimeConfigurationChanged : () -> {};

    Properties props = new Properties();
    props.putAll(consumerProperties);
    props.put(
        ConsumerConfig.GROUP_ID_CONFIG,
        "runtime-configuration-store-" + ProcessHandle.current().pid());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
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
            "RuntimeConfigurationStore did not become ready within " + timeout);
      }
      if (Thread.currentThread().isInterrupted()) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for RuntimeConfigurationStore");
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
            "RuntimeConfigurationStore: scheduler did not terminate within 5 s — proceeding with close");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("RuntimeConfigurationStore: interrupted while waiting for scheduler shutdown");
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
            "RuntimeConfigurationStore: topic '{}' has no partitions yet — using default config",
            topic);
        ready.set(true);
        return;
      }

      consumer.assign(topicPartitions);
      consumer.seekToBeginning(topicPartitions);
      Map<TopicPartition, Long> remaining = consumer.endOffsets(topicPartitions);
      remaining.entrySet().removeIf(entry -> entry.getValue() == 0);
      if (remaining.isEmpty()) {
        ready.set(true);
        return;
      }

      while (!remaining.isEmpty()) {
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, byte[]> configRecord : records) {
          applyRecord(configRecord);
          TopicPartition tp = new TopicPartition(configRecord.topic(), configRecord.partition());
          if (configRecord.offset() + 1 >= remaining.getOrDefault(tp, 0L)) {
            remaining.remove(tp);
          }
        }
      }
      ready.set(true);
      log.info(
          "RuntimeConfigurationStore: initial load complete — signingEnabled={} engineRequiresAuthorization={} replayProtectionMode={} replayProtectionRetentionMs={}",
          RuntimeConfigurationHolder.isSigningEnabled(),
          RuntimeConfigurationHolder.isEngineRequiresAuthorization(),
          RuntimeConfigurationHolder.getReplayProtectionMode(),
          RuntimeConfigurationHolder.getReplayProtectionRetentionMs());
    } catch (Exception e) {
      log.warn(
          "RuntimeConfigurationStore: initial load failed — using default config: {}",
          e.getMessage());
      ready.set(true);
    }
  }

  private void startBackgroundPoll() {
    scheduler.scheduleWithFixedDelay(
        () -> {
          try {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, byte[]> configRecord : records) {
              applyRecord(configRecord);
            }
          } catch (Exception e) {
            if (!scheduler.isShutdown()) {
              log.warn("RuntimeConfigurationStore background poll error: {}", e.getMessage());
            }
          }
        },
        1,
        1,
        TimeUnit.SECONDS);
  }

  private void applyRecord(ConsumerRecord<String, byte[]> configRecord) {
    if (!CONFIG_KEY.equals(configRecord.key())) {
      return;
    }
    if (configRecord.value() == null) {
      RuntimeConfigurationHolder.clear();
      notifyRuntimeConfigurationChanged();
      log.info("RuntimeConfigurationStore: cleared runtime configuration from tombstone");
      return;
    }
    try {
      ConfigurationEventDTO event =
          OBJECT_MAPPER.readValue(configRecord.value(), ConfigurationEventDTO.class);
      if (event != null && event.getConfiguration() != null) {
        RuntimeConfigurationHolder.set(event.getConfiguration());
        notifyRuntimeConfigurationChanged();
        log.info(
            "RuntimeConfigurationStore: updated config signingEnabled={} engineRequiresAuthorization={} replayProtectionMode={} replayProtectionRetentionMs={}",
            event.getConfiguration().isSigningEnabled(),
            event.getConfiguration().isEngineRequiresAuthorization(),
            event.getConfiguration().getReplayProtectionMode(),
            event.getConfiguration().getReplayProtectionRetentionMs());
      }
    } catch (Exception e) {
      log.warn(
          "RuntimeConfigurationStore: failed to deserialize config record key={}: {}",
          configRecord.key(),
          e.getMessage());
    }
  }

  private void notifyRuntimeConfigurationChanged() {
    try {
      onRuntimeConfigurationChanged.run();
    } catch (Exception e) {
      log.warn(
          "RuntimeConfigurationStore: runtime-configuration callback failed: {}", e.getMessage());
    }
  }
}
