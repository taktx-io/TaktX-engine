/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.UserTaskTriggerJsonDeserializer;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDDeserializer;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * This class is responsible for consuming user task trigger events from a Kafka topic and passing
 * them to a provided consumer.
 */
@Slf4j
public class UserTaskTriggerTopicConsumer {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private KafkaConsumer<UUID, UserTaskTriggerDTO> userTaskTriggerKafkaConsumer;
  private final Object consumerLock = new Object(); // Lock for synchronization
  private CompletableFuture<Void> consumerFuture;
  private volatile boolean running = false;

  /**
   * Constructor for UserTaskTriggerTopicConsumer.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   * @param executor the Executor to use for asynchronous processing
   */
  UserTaskTriggerTopicConsumer(TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  /**
   * Subscribes to the user task trigger topic and starts consuming messages, passing them to the
   * provided consumer.
   *
   * @param externalTaskTriggerConsumer the consumer to handle incoming UserTaskTriggerDTO messages
   */
  public void subscribeToUserTaskTriggerTopics(
      UserTaskTriggerConsumer externalTaskTriggerConsumer) {
    log.info("Subscribing to user task trigger topic");
    List<String> topics =
        List.of(
            taktPropertiesHelper.getPrefixedTopicName(
                Topics.USER_TASK_TRIGGER_TOPIC.getTopicName()));

    // Stop the previous consumer if it's running
    stop();

    // Wait for previous future to complete to avoid thread issues
    if (consumerFuture != null) {
      try {
        consumerFuture.join();
      } catch (Exception e) {
        log.warn("Error while waiting for previous consumer to complete", e);
      }
    }

    synchronized (consumerLock) {
      // Create a new consumer if needed
      if (userTaskTriggerKafkaConsumer == null) {
        userTaskTriggerKafkaConsumer = createConsumer();
      }

      // Subscribe to the topics
      userTaskTriggerKafkaConsumer.subscribe(topics);

      // Mark as running
      running = true;

      // Start the consumer in a new thread
      consumerFuture =
          CompletableFuture.runAsync(
              () -> {
                try {
                  while (running) {
                    synchronized (consumerLock) {
                      if (!running || userTaskTriggerKafkaConsumer == null) {
                        break;
                      }

                      ConsumerRecords<UUID, UserTaskTriggerDTO> records =
                          userTaskTriggerKafkaConsumer.poll(Duration.ofMillis(100));

                      for (ConsumerRecord<UUID, UserTaskTriggerDTO> externalTaskTriggerRecord :
                          records) {
                        externalTaskTriggerConsumer.accept(externalTaskTriggerRecord.value());
                      }
                    }
                  }
                } finally {
                  // Clean up the resources when the thread exits
                  synchronized (consumerLock) {
                    if (userTaskTriggerKafkaConsumer != null) {
                      try {
                        userTaskTriggerKafkaConsumer.unsubscribe();
                        userTaskTriggerKafkaConsumer.close();
                      } catch (Exception e) {
                        log.error("Error closing Kafka consumer", e);
                      } finally {
                        userTaskTriggerKafkaConsumer = null;
                      }
                    }
                  }
                }
              },
              executor);
    }
  }

  /** Stops the consumer from processing further records. */
  public void stop() {
    running = false;
  }

  /**
   * Creates a new KafkaConsumer with the appropriate configuration.
   *
   * @return a new KafkaConsumer instance
   */
  private <K, V> KafkaConsumer<K, V> createConsumer() {
    String groupId = "taktx-client-user-task-trigger-consumer";

    log.info("Creating consumer for group id {}", groupId);
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId, TaktUUIDDeserializer.class, UserTaskTriggerJsonDeserializer.class, "latest");
    return new KafkaConsumer<>(props);
  }
}
