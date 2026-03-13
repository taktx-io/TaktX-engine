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
import io.taktx.client.serdes.FaultTolerantExternalTaskTriggerDeserializer;
import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.serdes.DeserializationResult;
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
 * using the provided {@link ExternalTaskTriggerConsumer}.
 *
 * <p>Uses {@link FaultTolerantExternalTaskTriggerDeserializer} so that signature-verification
 * failures do not stop the consumer. When a record cannot be verified the decoded payload is still
 * available; the consumer uses the {@code processInstanceId} and {@code elementInstanceIdPath} from
 * the payload to report a BPMN incident back to the engine, then acknowledges and moves on.
 */
public class ExternalTaskTriggerTopicConsumer {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ExternalTaskTriggerTopicConsumer.class);
  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private final int consumerThreads;
  private final int maxPollRecords;
  private final int pollTimeoutMs;
  private final ProcessInstanceResponder processInstanceResponder;
  private List<KafkaConsumer<UUID, DeserializationResult<ExternalTaskTriggerDTO>>>
      externalTaskTriggerKafkaConsumers;
  private final Object consumerLock = new Object();
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
  ExternalTaskTriggerTopicConsumer(
      TaktPropertiesHelper taktPropertiesHelper,
      Executor executor,
      ProcessInstanceResponder processInstanceResponder) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
    this.processInstanceResponder = processInstanceResponder;
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
    if (!consumerFutures.isEmpty()) {
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
                KafkaConsumer<UUID, DeserializationResult<ExternalTaskTriggerDTO>> consumer =
                    createConsumer(groupId);
                try {
                  externalTaskTriggerKafkaConsumers.add(consumer);
                  consumer.subscribe(topics);

                  do {
                    if (!running) break;

                    ConsumerRecords<UUID, DeserializationResult<ExternalTaskTriggerDTO>> records;
                    try {
                      records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
                    } catch (org.apache.kafka.common.errors.WakeupException e) {
                      break;
                    } catch (Exception e) {
                      log.error("Fatal Kafka polling error, stopping consumer", e);
                      running = false;
                      break;
                    }

                    if (records.isEmpty()) continue;

                    // ── Separate successfully-decoded records from failures ──────────────
                    List<ConsumerRecord<UUID, DeserializationResult<ExternalTaskTriggerDTO>>>
                        failedRecords = new ArrayList<>();
                    List<ConsumerRecord<UUID, DeserializationResult<ExternalTaskTriggerDTO>>>
                        goodRecords = new ArrayList<>();

                    for (var rec : records) {
                      DeserializationResult<ExternalTaskTriggerDTO> result = rec.value();
                      if (result == null || !result.isSuccess()) {
                        failedRecords.add(rec);
                      } else {
                        goodRecords.add(rec);
                      }
                    }

                    // ── Report incidents for failed records ──────────────────────────────
                    for (var rec : failedRecords) {
                      DeserializationResult<ExternalTaskTriggerDTO> result = rec.value();
                      String error =
                          result != null ? result.getError() : "null deserialization result";
                      log.error(
                          "Deserialization/verification failed for record on topic={} partition={} offset={}: {}",
                          rec.topic(),
                          rec.partition(),
                          rec.offset(),
                          error);

                      if (result != null && result.hasValue()) {
                        // Body was decoded — we have processInstanceId and elementInstanceIdPath.
                        ExternalTaskTriggerDTO dto = result.getValue();
                        try {
                          processInstanceResponder
                              .responderForExternalTask(
                                  dto.getProcessInstanceId(), dto.getElementInstanceIdPath())
                              .respondIncident(
                                  "External task trigger failed verification: " + error,
                                  new String[0]);
                          log.warn(
                              "Reported incident for processInstanceId={} elementPath={} due to: {}",
                              dto.getProcessInstanceId(),
                              dto.getElementInstanceIdPath(),
                              error);
                        } catch (Exception reportEx) {
                          log.warn(
                              "Failed to report incident for processInstanceId={}",
                              dto.getProcessInstanceId(),
                              reportEx);
                        }
                      } else {
                        // Body could not be decoded at all — we have no routing info.
                        log.error(
                            "Cannot report incident: body could not be decoded for record at"
                                + " topic={} partition={} offset={}. Record will be skipped.",
                            rec.topic(),
                            rec.partition(),
                            rec.offset());
                      }
                      // Acknowledge the failed record so we don't re-process it forever.
                      try {
                        consumer.commitSync(
                            singletonMap(
                                new TopicPartition(rec.topic(), rec.partition()),
                                new OffsetAndMetadata(rec.offset() + 1)));
                      } catch (Exception commitEx) {
                        log.warn("Failed to commit offset for failed record", commitEx);
                      }
                    }

                    // ── Process good records grouped by jobId ────────────────────────────
                    if (goodRecords.isEmpty()) continue;

                    Map<String, List<ConsumerRecord<UUID, ExternalTaskTriggerDTO>>> recordsByJobId =
                        new java.util.HashMap<>();
                    for (var rec : goodRecords) {
                      String jobId =
                          rec.topic()
                              .replace(
                                  taktPropertiesHelper.getPrefixedTopicName(
                                      Constants.EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX),
                                  "");
                      // Unwrap the result into a plain ConsumerRecord<UUID, ExternalTaskTriggerDTO>
                      ConsumerRecord<UUID, ExternalTaskTriggerDTO> unwrapped =
                          new ConsumerRecord<>(
                              rec.topic(),
                              rec.partition(),
                              rec.offset(),
                              rec.timestamp(),
                              rec.timestampType(),
                              rec.serializedKeySize(),
                              rec.serializedValueSize(),
                              rec.key(),
                              rec.value().getValue(),
                              rec.headers(),
                              rec.leaderEpoch());
                      recordsByJobId.computeIfAbsent(jobId, k -> new ArrayList<>()).add(unwrapped);
                    }

                    for (String jobId : recordsByJobId.keySet()) {
                      List<ConsumerRecord<UUID, ExternalTaskTriggerDTO>> jobRecords =
                          recordsByJobId.get(jobId);
                      AckStrategy ackStrategy =
                          getEffectiveAckStrategy(jobId, externalTaskTriggerConsumer);
                      ThreadingStrategy threadingStrategy =
                          getEffectiveThreadingStrategy(jobId, externalTaskTriggerConsumer);

                      // Process batch with error handling
                      boolean batchProcessedSuccessfully =
                          processBatchWithErrorHandling(
                              externalTaskTriggerConsumer,
                              jobRecords,
                              threadingStrategy,
                              ackStrategy);

                      // Acknowledge only if processing was successful
                      if (batchProcessedSuccessfully) {
                        try {
                          acknowledgeRecords(consumer, jobRecords, ackStrategy);
                        } catch (Exception e) {
                          // Acknowledgment error - log but don't stop consumer
                          // Messages will be reprocessed on next poll
                          log.error(
                              "Error acknowledging messages for jobId {}, messages will be reprocessed",
                              jobId,
                              e);
                        }
                      } else {
                        log.warn(
                            "Batch processing failed for jobId {}, not acknowledging {} messages",
                            jobId,
                            jobRecords.size());
                        // Don't acknowledge - messages will be reprocessed
                        // For IMPLICIT strategy, seek back to avoid auto-commit
                        if (ackStrategy == AckStrategy.IMPLICIT) {
                          try {
                            for (var consumerRecord : jobRecords) {
                              consumer.seek(
                                  new TopicPartition(
                                      consumerRecord.topic(), consumerRecord.partition()),
                                  consumerRecord.offset());
                            }
                          } catch (Exception e) {
                            log.error("Error seeking back after failed batch", e);
                          }
                        }
                      }
                    }
                  } while (running);
                } catch (Throwable t) {
                  // Unexpected fatal error - stop consumer
                  log.error("Unexpected fatal error in consumer thread, stopping consumer", t);
                  running = false;
                } finally {
                  // Clean up the resources when the thread exits
                  synchronized (consumerLock) {
                    log.info("Cleaning up resources");
                    consumer.unsubscribe();
                    consumer.close();
                    externalTaskTriggerKafkaConsumers.remove(consumer);
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

    // Wake up all polling consumers so they unblock from poll() immediately
    // rather than waiting the full pollTimeoutMs before noticing running=false.
    synchronized (consumerLock) {
      if (externalTaskTriggerKafkaConsumers != null) {
        externalTaskTriggerKafkaConsumers.forEach(KafkaConsumer::wakeup);
      }
    }

    // Wait for all consumer futures to complete
    if (!consumerFutures.isEmpty()) {
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
            FaultTolerantExternalTaskTriggerDeserializer.class,
            "latest");

    // Override max poll records with our configured value
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);

    return new KafkaConsumer<>(props);
  }

  /**
   * Process a batch of records with error handling that doesn't stop the consumer.
   *
   * @return true if batch was processed successfully, false if any error occurred
   */
  private boolean processBatchWithErrorHandling(
      ExternalTaskTriggerConsumer consumer,
      List<ConsumerRecord<UUID, ExternalTaskTriggerDTO>> jobRecords,
      ThreadingStrategy threadingStrategy,
      AckStrategy ackStrategy) {

    List<ExternalTaskTriggerDTO> batch = jobRecords.stream().map(ConsumerRecord::value).toList();

    try {
      switch (threadingStrategy) {
        case SINGLE_THREAD -> {
          // Process batch synchronously
          try {
            consumer.acceptBatch(batch);
            return true;
          } catch (Exception e) {
            log.error(
                "Error processing batch of {} messages in SINGLE_THREAD mode", batch.size(), e);
            return false;
          }
        }
        case VIRTUAL_THREAD_WAIT -> {
          // Process each message in parallel and wait for all to complete
          List<CompletableFuture<Boolean>> futures = new ArrayList<>();
          for (ExternalTaskTriggerDTO dto : batch) {
            futures.add(
                CompletableFuture.supplyAsync(
                    () -> {
                      try {
                        consumer.acceptBatch(List.of(dto));
                        return true;
                      } catch (Exception e) {
                        log.error(
                            "Error processing message {} in VIRTUAL_THREAD_WAIT mode",
                            dto.getExternalTaskId(),
                            e);
                        return false;
                      }
                    },
                    virtualThreadExecutor));
          }

          // Wait for all and check if all succeeded
          try {
            CompletableFuture<Void> allOf =
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.join();

            // Check if all succeeded
            boolean allSucceeded = futures.stream().allMatch(f -> f.getNow(false));
            if (!allSucceeded) {
              log.warn(
                  "Some messages failed processing in VIRTUAL_THREAD_WAIT mode, batch considered failed");
            }
            return allSucceeded;
          } catch (Exception e) {
            log.error("Error waiting for virtual threads to complete", e);
            return false;
          }
        }
        case VIRTUAL_THREAD_FIRE_AND_FORGET -> {
          // Fire and forget - we can't track success, so we assume success
          // This is inherently unsafe for acknowledgment
          if (ackStrategy != AckStrategy.IMPLICIT) {
            log.warn(
                "VIRTUAL_THREAD_FIRE_AND_FORGET with explicit acknowledgment is unsafe - messages may be lost");
          }

          for (ExternalTaskTriggerDTO dto : batch) {
            CompletableFuture.runAsync(
                () -> {
                  try {
                    consumer.acceptBatch(List.of(dto));
                  } catch (Exception e) {
                    log.error(
                        "Error processing message {} in VIRTUAL_THREAD_FIRE_AND_FORGET mode (message may be lost)",
                        dto.getExternalTaskId(),
                        e);
                  }
                },
                virtualThreadExecutor);
          }
          // Return true immediately as we don't wait
          return true;
        }
      }
    } catch (Exception e) {
      log.error("Unexpected error in batch processing", e);
      return false;
    }

    return false; // Should never reach here
  }

  /** Acknowledge records based on the ack strategy. Only called if processing was successful. */
  private void acknowledgeRecords(
      KafkaConsumer<UUID, DeserializationResult<ExternalTaskTriggerDTO>> consumer,
      List<ConsumerRecord<UUID, ExternalTaskTriggerDTO>> jobRecords,
      AckStrategy ackStrategy) {

    switch (ackStrategy) {
      case IMPLICIT -> {
        // Default Kafka auto-commit, do nothing
        // Consumer will auto-commit based on auto.commit.interval.ms
      }
      case EXPLICIT_BATCH -> {
        // Commit all offsets synchronously after successful batch processing
        consumer.commitSync();
        log.debug("Committed batch of {} messages", jobRecords.size());
      }
      case EXPLICIT_MESSAGE -> {
        // Commit each message offset individually
        for (ConsumerRecord<UUID, ExternalTaskTriggerDTO> consumerRecord : jobRecords) {
          consumer.commitSync(
              singletonMap(
                  new TopicPartition(consumerRecord.topic(), consumerRecord.partition()),
                  new OffsetAndMetadata(consumerRecord.offset() + 1)));
        }
        log.debug("Committed {} individual message offsets", jobRecords.size());
      }
    }
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
