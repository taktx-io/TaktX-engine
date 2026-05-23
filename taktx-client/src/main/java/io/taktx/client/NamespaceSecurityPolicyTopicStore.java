/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.proto.NamespaceSecurityPolicyMessage;
import io.taktx.security.NamespaceSecurityPolicyControlPlaneContract;
import io.taktx.serdes.NamespaceSecurityPolicyProtoMapper;
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

/** Watches the compacted namespace-security-policy topic for the client runtime. */
final class NamespaceSecurityPolicyTopicStore implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(NamespaceSecurityPolicyTopicStore.class);

  private final KafkaConsumer<String, byte[]> consumer;
  private final String topic;
  private final ClientNamespaceSecurityPolicyStore store;
  private final Runnable onPolicyChanged;
  private final AtomicBoolean ready = new AtomicBoolean(false);
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "namespace-security-policy-store-poll");
            thread.setDaemon(true);
            return thread;
          });

  NamespaceSecurityPolicyTopicStore(
      Properties consumerProperties,
      String topic,
      ClientNamespaceSecurityPolicyStore store,
      Runnable onPolicyChanged) {
    this.topic = topic;
    this.store = store;
    this.onPolicyChanged = onPolicyChanged != null ? onPolicyChanged : () -> {};

    Properties props = new Properties();
    props.putAll(consumerProperties);
    props.put(
        ConsumerConfig.GROUP_ID_CONFIG,
        "namespace-security-policy-store-" + ProcessHandle.current().pid());
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
            "NamespaceSecurityPolicyTopicStore did not become ready within " + timeout);
      }
      if (Thread.currentThread().isInterrupted()) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while waiting for NamespaceSecurityPolicyTopicStore");
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
            "NamespaceSecurityPolicyTopicStore: scheduler did not terminate within 5 s — proceeding with close");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn(
          "NamespaceSecurityPolicyTopicStore: interrupted while waiting for scheduler shutdown");
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
            "NamespaceSecurityPolicyTopicStore: topic '{}' has no partitions yet — using default open behavior",
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
        for (ConsumerRecord<String, byte[]> record : records) {
          applyRecord(record);
          TopicPartition tp = new TopicPartition(record.topic(), record.partition());
          if (record.offset() + 1 >= remaining.getOrDefault(tp, 0L)) {
            remaining.remove(tp);
          }
        }
      }
      ready.set(true);
      NamespaceSecurityPolicyDTO authoritative = store.getAuthoritativePolicy();
      log.info(
          "NamespaceSecurityPolicyTopicStore: initial load complete — currentState={} activePolicyVersion={} activePolicyHash={}",
          store.get() != null ? store.get().getActivationState() : null,
          authoritative != null ? authoritative.getActivePolicyVersion() : null,
          authoritative != null ? authoritative.getActivePolicyHash() : null);
    } catch (Exception e) {
      log.warn(
          "NamespaceSecurityPolicyTopicStore: initial load failed — defaulting to open behavior: {}",
          e.getMessage());
      ready.set(true);
    }
  }

  private void startBackgroundPoll() {
    scheduler.scheduleWithFixedDelay(
        () -> {
          try {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, byte[]> record : records) {
              applyRecord(record);
            }
          } catch (Exception e) {
            if (!scheduler.isShutdown()) {
              log.warn(
                  "NamespaceSecurityPolicyTopicStore background poll error: {}", e.getMessage());
            }
          }
        },
        1,
        1,
        TimeUnit.SECONDS);
  }

  private void applyRecord(ConsumerRecord<String, byte[]> record) {
    if (!NamespaceSecurityPolicyControlPlaneContract.policyRecordKey().equals(record.key())) {
      return;
    }
    if (record.value() == null) {
      store.clear();
      notifyPolicyChanged();
      log.info("NamespaceSecurityPolicyTopicStore: cleared policy from tombstone");
      return;
    }
    try {
      NamespaceSecurityPolicyDTO policy =
          NamespaceSecurityPolicyProtoMapper.toDto(
              NamespaceSecurityPolicyMessage.parseFrom(record.value()));
      store.update(policy);
      notifyPolicyChanged();
      NamespaceSecurityPolicyDTO authoritative = store.getAuthoritativePolicy();
      log.info(
          "NamespaceSecurityPolicyTopicStore: updated policy activationState={} desiredPolicyVersion={} activePolicyVersion={} activePolicyHash={}",
          policy.getActivationState(),
          policy.getDesiredPolicyVersion(),
          authoritative != null ? authoritative.getActivePolicyVersion() : null,
          authoritative != null ? authoritative.getActivePolicyHash() : null);
    } catch (Exception e) {
      log.warn(
          "NamespaceSecurityPolicyTopicStore: failed to deserialize policy record key={}: {}",
          record.key(),
          e.getMessage());
    }
  }

  private void notifyPolicyChanged() {
    try {
      onPolicyChanged.run();
    } catch (Exception e) {
      log.warn(
          "NamespaceSecurityPolicyTopicStore: policy-change callback failed: {}", e.getMessage());
    }
  }
}

