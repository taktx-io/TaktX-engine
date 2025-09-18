/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.client.serdes.ExternalTaskTriggerJsonDeserializer;
import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDDeserializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

@Slf4j
public class ExternalTaskTriggerTopicConsumer {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private final int consumerThreads;
  private final int maxPollRecords;
  private final int pollTimeoutMs;
  private List<KafkaConsumer<UUID, ExternalTaskTriggerDTO>> externalTaskTriggerKafkaConsumers;
  private final Object consumerLock = new Object(); // Lock for synchronization
  private List<CompletableFuture<Void>> consumerFutures = new ArrayList<>();
  private volatile boolean running = false;

  ExternalTaskTriggerTopicConsumer(TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
    this.consumerThreads = taktPropertiesHelper.getExternalTaskConsumerThreads();
    this.maxPollRecords = taktPropertiesHelper.getExternalTaskConsumerMaxPollRecords();
    this.pollTimeoutMs = taktPropertiesHelper.getExternalTaskConsumerPollTimeoutMs();

    log.info(
        "ExternalTaskTriggerTopicConsumer configured with {} consumer threads, {} max poll records, {}ms poll timeout",
        consumerThreads,
        maxPollRecords,
        pollTimeoutMs);
  }

  public void subscribeToExternalTaskTriggerTopics(
      ExternalTaskTriggerConsumer externalTaskTriggerConsumer, String groupId) {
    log.info("Subscribing to job ids {}", externalTaskTriggerConsumer.getJobIds());
    List<String> topics =
        externalTaskTriggerConsumer.getJobIds().stream()
            .map(
                jobId ->
                    taktPropertiesHelper.getPrefixedTopicName(
                        Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX + jobId))
            .toList();

    // Stop the previous consumesr if it's running
    stop();

    // Wait for previous future to complete to avoid thread issues
    if (consumerFutures != null && !consumerFutures.isEmpty()) {
      try {
        for (CompletableFuture<Void> consumerFuture : consumerFutures) {
          consumerFuture.join();
        }
      } catch (Exception e) {
        log.warn("Error while waiting for previous consumer to complete", e);
      }
    }

    // Create a new consumer if needed
    if (externalTaskTriggerKafkaConsumers == null) {
      externalTaskTriggerKafkaConsumers = new ArrayList<>();
    }

    // Mark as running
    running = true;
    for (int i = 0; i < consumerThreads; i++) {
      CompletableFuture<Void> consumerFuture =
          CompletableFuture.runAsync(
              () -> {
                KafkaConsumer<UUID, ExternalTaskTriggerDTO> consumer = createConsumer(groupId);
                try {
                  externalTaskTriggerKafkaConsumers.add(consumer);
                  consumer.subscribe(topics);

                  do {
                    if (!running || consumer == null) {
                      break;
                    }

                    ConsumerRecords<UUID, ExternalTaskTriggerDTO> records =
                        consumer.poll(Duration.ofMillis(pollTimeoutMs));
                    if (records.isEmpty()) {
                      continue;
                    }

                    List<ExternalTaskTriggerDTO> batch = new ArrayList<>(records.count());
                    for (ConsumerRecord<UUID, ExternalTaskTriggerDTO> record : records) {
                      batch.add(record.value());
                    }
                    externalTaskTriggerConsumer.acceptBatch(batch);
                  } while (running);
                } finally {
                  // Clean up the resources when the thread exits
                  synchronized (consumerLock) {
                    log.info("Cleaning up resources");
                    if (consumer != null) {
                      consumer.unsubscribe();
                      consumer.close();
                      externalTaskTriggerKafkaConsumers.remove(consumer);
                      consumer = null;
                    }
                  }
                }
              },
              executor);
      consumerFutures.add(consumerFuture);
    }
  }

  public void stop() {
    running = false;

    // Wait for all consumer futures to complete
    if (consumerFutures != null && !consumerFutures.isEmpty()) {
      try {
        for (CompletableFuture<Void> future : consumerFutures) {
          future.join();
        }
      } catch (Exception e) {
        log.warn("Error waiting for consumers to stop", e);
      }
      consumerFutures.clear();
    }

    // Clear the consumers list
    synchronized (consumerLock) {
      if (externalTaskTriggerKafkaConsumers != null) {
        externalTaskTriggerKafkaConsumers.clear();
      }
    }
  }

  private <K, V> KafkaConsumer<K, V> createConsumer(String groupId) {
    log.info("Creating consumer for group id {}", groupId);
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId,
            TaktUUIDDeserializer.class,
            ExternalTaskTriggerJsonDeserializer.class,
            "latest");

    // Override max poll records with our configured value
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);

    return new KafkaConsumer<>(props);
  }
}
