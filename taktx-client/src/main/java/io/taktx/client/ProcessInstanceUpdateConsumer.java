/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private boolean running = false;
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
  }

  private void subscribeToTopic(String groupId) {
    running = true;

    CompletableFuture.runAsync(
        () -> {
          try (KafkaConsumer<UUID, InstanceUpdateDTO> consumer = createConsumer(groupId)) {

            String prefixedTopicName =
                taktPropertiesHelper.getPrefixedTopicName(
                    Topics.INSTANCE_UPDATE_TOPIC.getTopicName());

            consumer.subscribe(Collections.singletonList(prefixedTopicName));

            while (running) {
              consumeRecords(consumer);
            }

            consumer.unsubscribe();
          }
        },
        executor);
  }

  private void consumeRecords(KafkaConsumer<UUID, InstanceUpdateDTO> consumer) {
    ConsumerRecords<UUID, InstanceUpdateDTO> poll = consumer.poll(Duration.ofMillis(100));

    List<InstanceUpdateRecord> records = new ArrayList<>();
    poll.forEach(
        instanceUpdateConsumerRecord ->
            records.add(
                new InstanceUpdateRecord(
                    instanceUpdateConsumerRecord.timestamp(),
                    instanceUpdateConsumerRecord.key(),
                    instanceUpdateConsumerRecord.value(),
                    instanceUpdateConsumerRecord.partition(),
                    instanceUpdateConsumerRecord.offset())));

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
