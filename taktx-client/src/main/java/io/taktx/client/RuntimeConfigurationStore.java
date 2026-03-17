/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
final class RuntimeConfigurationStore implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(RuntimeConfigurationStore.class);
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final String CONFIG_KEY = "config";

  private final KafkaConsumer<String, byte[]> consumer;
  private final String topic;
  private final AtomicBoolean ready = new AtomicBoolean(false);
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "runtime-configuration-store-poll");
            thread.setDaemon(true);
            return thread;
          });

  RuntimeConfigurationStore(Properties consumerProperties, String topic) {
    this.topic = topic;

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
          "RuntimeConfigurationStore: initial load complete — signingEnabled={} authorizationEnabled={}",
          RuntimeConfigurationHolder.isSigningEnabled(),
          RuntimeConfigurationHolder.isAuthorizationEnabled());
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
      log.info("RuntimeConfigurationStore: cleared runtime configuration from tombstone");
      return;
    }
    try {
      ConfigurationEventDTO event =
          OBJECT_MAPPER.readValue(configRecord.value(), ConfigurationEventDTO.class);
      if (event != null && event.getConfiguration() != null) {
        RuntimeConfigurationHolder.set(event.getConfiguration());
        log.info(
            "RuntimeConfigurationStore: updated config signingEnabled={} authorizationEnabled={}",
            event.getConfiguration().isSigningEnabled(),
            event.getConfiguration().isAuthorizationEnabled());
      }
    } catch (Exception e) {
      log.warn(
          "RuntimeConfigurationStore: failed to deserialize config record key={}: {}",
          configRecord.key(),
          e.getMessage());
    }
  }
}
