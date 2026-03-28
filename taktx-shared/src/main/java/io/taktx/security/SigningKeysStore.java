/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * Live cache of all {@link SigningKeyDTO} entries from the compacted {@code taktx-signing-keys}
 * topic.
 *
 * <h3>Bootstrap guarantee</h3>
 *
 * <p>Call {@link #awaitReady(Duration)} before consuming any signed trigger topics. This blocks
 * until the store has read the topic to end-of-topic, guaranteeing the engine's key is present
 * before the first trigger record arrives. A missing {@code keyId} after that point is always a
 * security violation, never a timing issue.
 *
 * <h3>Key rotation</h3>
 *
 * <p>A background thread continuously polls the topic for updates. When the engine publishes a key
 * rotation (status change to {@code TRUSTED} or {@code REVOKED}), the store picks it up within one
 * poll interval (~1 second) and callers will immediately see the updated status.
 *
 * <h3>Lifecycle</h3>
 *
 * <p>Call {@link #close()} when the worker shuts down to release the background consumer thread.
 */
public class SigningKeysStore implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(SigningKeysStore.class);
  private static final ObjectMapper CBOR =
      new ObjectMapper(new CBORFactory()).registerModule(new JavaTimeModule());

  private final ConcurrentHashMap<String, SigningKeyDTO> keys = new ConcurrentHashMap<>();
  private final AtomicBoolean ready = new AtomicBoolean(false);
  private final KafkaConsumer<String, byte[]> consumer;
  private final String topic;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "signing-keys-store-poll");
            t.setDaemon(true);
            return t;
          });

  /**
   * Creates and starts a {@code SigningKeysStore} using a fully-configured {@code Properties}
   * object. This constructor follows the same pattern as {@code ProcessDefinitionDeployer} and
   * {@code MessageEventSender}: the caller passes the properties produced by {@link
   * io.taktx.util.TaktPropertiesHelper#getKafkaConsumerProperties} so that all auth/TLS settings
   * are inherited automatically.
   *
   * @param consumerProperties fully-configured Kafka consumer properties (auth/TLS already merged)
   * @param topic fully-qualified (namespaced) topic name, e.g. {@code "default.taktx-signing-keys"}
   */
  public SigningKeysStore(Properties consumerProperties, String topic) {
    this.topic = topic;

    // Build on top of the provided properties, but force the settings that the store requires.
    Properties props = new Properties();
    props.putAll(consumerProperties);
    props.put(
        ConsumerConfig.GROUP_ID_CONFIG, "signing-keys-store-" + ProcessHandle.current().pid());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

    this.consumer = new KafkaConsumer<>(props);
    initialLoad();
    startBackgroundPoll();
  }

  /**
   * Looks up a signing key by ID. Returns {@code null} if the key is not known — which is always a
   * security violation (never a timing issue after {@link #awaitReady} completes).
   */
  public SigningKeyDTO get(String keyId) {
    return keys.get(keyId);
  }

  /**
   * Returns the public key base64 for the given keyId, or {@code null} if not found or if the key
   * is {@link KeyStatus#REVOKED}.
   */
  public String getPublicKeyBase64(String keyId) {
    SigningKeyDTO dto = keys.get(keyId);
    if (dto == null || dto.getStatus() == KeyStatus.REVOKED) return null;
    return dto.getPublicKeyBase64();
  }

  /** Returns {@code true} once the initial load from beginning to end-of-topic has completed. */
  public boolean isReady() {
    return ready.get();
  }

  /**
   * Blocks until the store has finished its initial load, or the timeout expires.
   *
   * @throws IllegalStateException if the timeout is exceeded before the store is ready
   */
  public void awaitReady(Duration timeout) {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    while (!ready.get()) {
      if (System.currentTimeMillis() > deadline) {
        throw new IllegalStateException("SigningKeysStore did not become ready within " + timeout);
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for SigningKeysStore", e);
      }
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
    try {
      // Wait for the background poll task to finish. This is required because KafkaConsumer
      // is not thread-safe: if the poll thread is still inside consumer.poll() when close()
      // is called from another thread, Kafka throws ConcurrentModificationException.
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        log.warn(
            "SigningKeysStore: scheduler did not terminate within 5 s — proceeding with close");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("SigningKeysStore: interrupted while waiting for scheduler shutdown");
    }
    consumer.close();
    log.info("SigningKeysStore closed");
  }

  // ── private ────────────────────────────────────────────────────────────────

  /** Synchronously reads all partitions of the topic from offset 0 to current end-of-topic. */
  private void initialLoad() {
    try {
      var partitionInfos = consumer.partitionsFor(topic);
      if (partitionInfos == null || partitionInfos.isEmpty()) {
        log.warn("SigningKeysStore: topic '{}' has no partitions yet — store will be empty", topic);
        ready.set(true);
        return;
      }

      var topicPartitions =
          partitionInfos.stream()
              .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
              .toList();
      consumer.assign(topicPartitions);
      consumer.seekToBeginning(topicPartitions);

      // Determine end offsets — we read until we reach them
      Map<TopicPartition, Long> endOffsets = consumer.endOffsets(topicPartitions);
      Map<TopicPartition, Long> remaining = new HashMap<>(endOffsets);
      // Remove partitions that are already empty
      remaining.entrySet().removeIf(e -> e.getValue() == 0);

      if (remaining.isEmpty()) {
        log.info("SigningKeysStore: topic '{}' is empty, no keys to load", topic);
        ready.set(true);
        return;
      }

      while (!remaining.isEmpty()) {
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, byte[]> consumerRecord : records) {
          applyRecord(consumerRecord);
          TopicPartition tp =
              new TopicPartition(consumerRecord.topic(), consumerRecord.partition());
          // offset is 0-based; end offset points past the last message
          if (consumerRecord.offset() + 1 >= remaining.getOrDefault(tp, 0L)) {
            remaining.remove(tp);
          }
        }
      }

      log.info("SigningKeysStore: initial load complete — {} key(s) loaded", keys.size());
      ready.set(true);
    } catch (Exception e) {
      log.error(
          "SigningKeysStore: initial load failed — store may be incomplete: {}", e.getMessage(), e);
      ready.set(true); // still mark ready so we don't block forever; callers will get null lookups
    }
  }

  /** Polls for new/updated key records in the background approximately every second. */
  private void startBackgroundPoll() {
    scheduler.scheduleWithFixedDelay(
        () -> {
          try {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, byte[]> consumerRecord : records) {
              applyRecord(consumerRecord);
            }
          } catch (Exception e) {
            log.warn("SigningKeysStore background poll error: {}", e.getMessage());
          }
        },
        1,
        1,
        TimeUnit.SECONDS);
  }

  private void applyRecord(ConsumerRecord<String, byte[]> consumerRecord) {
    if (consumerRecord.value() == null) {
      // Tombstone — remove the key
      keys.remove(consumerRecord.key());
      log.info("SigningKeysStore: removed key keyId={}", consumerRecord.key());
      return;
    }
    try {
      SigningKeyDTO dto = CBOR.readValue(consumerRecord.value(), SigningKeyDTO.class);
      keys.put(dto.getKeyId(), dto);
      log.debug(
          "SigningKeysStore: loaded keyId={} status={} owner={}",
          dto.getKeyId(),
          dto.getStatus(),
          dto.getOwner());
    } catch (Exception e) {
      log.warn(
          "SigningKeysStore: failed to deserialise key record keyId={}: {}",
          consumerRecord.key(),
          e.getMessage());
    }
  }

  /** Returns an unmodifiable snapshot of all currently known keys (for diagnostics). */
  public Map<String, SigningKeyDTO> snapshot() {
    return Collections.unmodifiableMap(new HashMap<>(keys));
  }
}
