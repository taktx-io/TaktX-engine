/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import static java.util.Collections.singletonMap;

import io.taktx.client.annotation.AckStrategy;
import io.taktx.client.annotation.ThreadingStrategy;
import io.taktx.client.serdes.ExternalTaskTriggerJsonDeserializer;
import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDDeserializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;

/**
 * A Kafka consumer that subscribes to external task trigger topics and processes incoming messages
 * using the provided ExternalTaskTriggerConsumer.
 */
public class ExternalTaskTriggerTopicConsumer {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ExternalTaskTriggerTopicConsumer.class);
  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private final int consumerThreads;
  private final int maxPollRecords;
  private final int pollTimeoutMs;
  private List<KafkaConsumer<UUID, ExternalTaskTriggerDTO>> externalTaskTriggerKafkaConsumers;
  private final Object consumerLock = new Object(); // Lock for synchronization
  private final List<CompletableFuture<Void>> consumerFutures = new ArrayList<>();
  private volatile boolean running = false;

  // Virtual thread executor for job handling
  private final Executor virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Constructor for ExternalTaskTriggerTopicConsumer.
   *
   * @param taktPropertiesHelper The TaktPropertiesHelper for configuration.
   * @param executor The executor for running consumer threads.
   */
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

  /**
   * Subscribe to external task trigger topics based on job IDs from the provided consumer.
   *
   * @param externalTaskTriggerConsumer the consumer to handle incoming ExternalTaskTriggerDTO
   *     messages
   * @param groupId the Kafka consumer group ID to use for this subscription
   */
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

                    // Group records by topic/jobId
                    Map<String, List<ConsumerRecord<UUID, ExternalTaskTriggerDTO>>> recordsByJobId =
                        new java.util.HashMap<>();
                    for (ConsumerRecord<UUID, ExternalTaskTriggerDTO> record : records) {
                      String jobId =
                          record.topic().replace(Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX, "");
                      recordsByJobId.computeIfAbsent(jobId, k -> new ArrayList<>()).add(record);
                    }

                    for (String jobId : recordsByJobId.keySet()) {
                      List<ConsumerRecord<UUID, ExternalTaskTriggerDTO>> jobRecords =
                          recordsByJobId.get(jobId);
                      AckStrategy ackStrategy =
                          getEffectiveAckStrategy(jobId, externalTaskTriggerConsumer);
                      ThreadingStrategy threadingStrategy =
                          getEffectiveThreadingStrategy(jobId, externalTaskTriggerConsumer);

                      // Prepare batch
                      List<ExternalTaskTriggerDTO> batch =
                          jobRecords.stream().map(ConsumerRecord::value).toList();

                      // Threading strategy
                      switch (threadingStrategy) {
                        case SINGLE_THREAD -> {
                          externalTaskTriggerConsumer.acceptBatch(batch);
                        }
                        case VIRTUAL_THREAD_WAIT -> {
                          List<CompletableFuture<Void>> futures = new ArrayList<>();
                          for (ExternalTaskTriggerDTO dto : batch) {
                            futures.add(
                                CompletableFuture.runAsync(
                                    () -> externalTaskTriggerConsumer.acceptBatch(List.of(dto)),
                                    virtualThreadExecutor));
                          }
                          CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                        }
                        case VIRTUAL_THREAD_FIRE_AND_FORGET -> {
                          for (ExternalTaskTriggerDTO dto : batch) {
                            CompletableFuture.runAsync(
                                () -> externalTaskTriggerConsumer.acceptBatch(List.of(dto)),
                                virtualThreadExecutor);
                          }
                        }
                      }

                      // Ack strategy
                      switch (ackStrategy) {
                        case IMPLICIT -> {
                          // Default Kafka auto-commit, do nothing
                        }
                        case EXPLICIT_BATCH -> {
                          consumer.commitSync();
                        }
                        case EXPLICIT_MESSAGE -> {
                          for (ConsumerRecord<UUID, ExternalTaskTriggerDTO> record : jobRecords) {
                            consumer.commitSync(
                                singletonMap(
                                    new TopicPartition(record.topic(), record.partition()),
                                    new OffsetAndMetadata(record.offset() + 1)));
                          }
                        }
                      }
                    }
                  } while (running);
                } catch (Throwable t) {
                  log.error("Error in consumer thread", t);
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

  /** Stops all running consumers and waits for them to finish processing. */
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

  private AckStrategy getEffectiveAckStrategy(String jobId, ExternalTaskTriggerConsumer consumer) {
    // 1. Annotation
    if (consumer instanceof AnnotationScanningExternalTaskTriggerConsumer asConsumer) {
      AckStrategy annotationStrategy = asConsumer.getAckStrategy(jobId);
      if (annotationStrategy != null) {
        return annotationStrategy;
      }
    }
    // 2. Config property
    String configValue = taktPropertiesHelper.getExternalTaskAckStrategy();
    if (configValue != null) {
      try {
        return AckStrategy.valueOf(configValue.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
      }
    }
    // 3. Default
    return AckStrategy.EXPLICIT_BATCH;
  }

  private ThreadingStrategy getEffectiveThreadingStrategy(
      String jobId, ExternalTaskTriggerConsumer consumer) {
    // 1. Annotation
    if (consumer instanceof AnnotationScanningExternalTaskTriggerConsumer asConsumer) {
      ThreadingStrategy annotationStrategy = asConsumer.getThreadingStrategy(jobId);
      if (annotationStrategy != null) {
        return annotationStrategy;
      }
    }
    // 2. Config property
    String configValue = taktPropertiesHelper.getEffectiveThreadingStrategy();
    if (configValue != null) {
      try {
        return ThreadingStrategy.valueOf(configValue.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
      }
    }
    // 3. Default
    return ThreadingStrategy.VIRTUAL_THREAD_WAIT;
  }
}
