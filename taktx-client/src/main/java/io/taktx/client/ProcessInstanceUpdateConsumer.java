/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.InstanceUpdateJsonDeserializer;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDDeserializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;

/**
 * This class is responsible for managing the subscription to external tasks for all process
 * definitions.
 */
public class ProcessInstanceUpdateConsumer {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ProcessInstanceUpdateConsumer.class);

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private volatile boolean running = false;
  private volatile KafkaConsumer<UUID, InstanceUpdateDTO> activeConsumer;
  private final List<Consumer<List<InstanceUpdateRecord>>> instanceUpdateConsumers =
      new ArrayList<>();

  /**
   * Constructor for ProcessInstanceUpdateConsumer.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   * @param executor the Executor to use for asynchronous processing
   */
  public ProcessInstanceUpdateConsumer(
      TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  /**
   * Registers a consumer that will be notified of instance update records.
   *
   * @param groupId the Kafka consumer group ID to use
   * @param consumer the consumer to register
   */
  public void registerInstanceUpdateConsumer(
      String groupId, Consumer<List<InstanceUpdateRecord>> consumer) {
    if (instanceUpdateConsumers.isEmpty()) {
      subscribeToTopic(groupId);
    }
    instanceUpdateConsumers.add(consumer);
  }

  /** Stops the consumer from processing further records. */
  public void stop() {
    running = false;
    KafkaConsumer<UUID, InstanceUpdateDTO> c = activeConsumer;
    if (c != null) {
      c.wakeup();
    }
  }

  private void subscribeToTopic(String groupId) {
    running = true;

    CompletableFuture.runAsync(
        () -> {
          try (KafkaConsumer<UUID, InstanceUpdateDTO> consumer = createConsumer(groupId)) {
            activeConsumer = consumer;

            String prefixedTopicName =
                taktPropertiesHelper.getPrefixedTopicName(
                    Topics.INSTANCE_UPDATE_TOPIC.getTopicName());

            consumer.subscribe(Collections.singletonList(prefixedTopicName));

            try {
              while (running) {
                consumeRecords(consumer);
              }
            } catch (org.apache.kafka.common.errors.WakeupException e) {
              // stop() was called — exit cleanly
            } finally {
              activeConsumer = null;
              consumer.unsubscribe();
            }
          }
        },
        executor);
  }

  private void consumeRecords(KafkaConsumer<UUID, InstanceUpdateDTO> consumer) {
    ConsumerRecords<UUID, InstanceUpdateDTO> poll;
    try {
      poll = consumer.poll(Duration.ofMillis(100));
    } catch (org.apache.kafka.common.errors.RecordDeserializationException e) {
      log.error(
          "Failed to deserialise InstanceUpdateDTO on topic={} partition={} offset={} — seeking past poison record: {}",
          e.topicPartition().topic(),
          e.topicPartition().partition(),
          e.offset(),
          e.getMessage());
      consumer.seek(e.topicPartition(), e.offset() + 1);
      return;
    }

    List<InstanceUpdateRecord> records = new ArrayList<>();
    for (var rec : poll) {
      if (rec.value() == null) {
        log.error(
            "Null InstanceUpdateDTO value on topic={} partition={} offset={} — skipping record",
            rec.topic(),
            rec.partition(),
            rec.offset());
        continue;
      }
      records.add(
          new InstanceUpdateRecord(
              rec.timestamp(), rec.key(), rec.value(), rec.partition(), rec.offset()));
    }

    if (!records.isEmpty()) {
      instanceUpdateConsumers.forEach(
          instanceUpdateConsumer -> instanceUpdateConsumer.accept(records));
    }
  }

  private <K, V> KafkaConsumer<K, V> createConsumer(String groupId) {
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId, TaktUUIDDeserializer.class, InstanceUpdateJsonDeserializer.class, "latest");
    return new KafkaConsumer<>(props);
  }
}
