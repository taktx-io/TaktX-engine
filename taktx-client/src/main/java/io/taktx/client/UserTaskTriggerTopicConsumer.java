/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.FaultTolerantUserTaskTriggerDeserializer;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.serdes.DeserializationResult;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDDeserializer;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;

/**
 * Consumes user task trigger events from a Kafka topic and passes them to the registered consumer.
 *
 * <p>Uses {@link FaultTolerantUserTaskTriggerDeserializer} so that signature-verification failures
 * do not stop the consumer. When a record fails verification but the body was decoded, an incident
 * is reported back to the engine using the decoded {@code processInstanceId} and {@code
 * elementInstanceIdPath}. Records whose body cannot be decoded at all are skipped and logged; the
 * consumer keeps running in both cases.
 */
public class UserTaskTriggerTopicConsumer {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(UserTaskTriggerTopicConsumer.class);
  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private final ProcessInstanceResponder processInstanceResponder;
  private KafkaConsumer<UUID, DeserializationResult<UserTaskTriggerDTO>>
      userTaskTriggerKafkaConsumer;
  private final Object consumerLock = new Object();
  private CompletableFuture<Void> consumerFuture;
  private volatile boolean running = false;

  /**
   * Constructor for UserTaskTriggerTopicConsumer.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   * @param executor the Executor to use for asynchronous processing
   * @param processInstanceResponder the ProcessInstanceResponder to use for reporting errors
   */
  UserTaskTriggerTopicConsumer(
      TaktPropertiesHelper taktPropertiesHelper,
      Executor executor,
      ProcessInstanceResponder processInstanceResponder) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
    this.processInstanceResponder = processInstanceResponder;
  }

  /**
   * Subscribes to the user task trigger topic and starts consuming messages.
   *
   * @param userTaskTriggerConsumer the consumer to handle incoming {@link UserTaskTriggerDTO}
   *     messages
   */
  public void subscribeToUserTaskTriggerTopics(UserTaskTriggerConsumer userTaskTriggerConsumer) {
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
                      if (!running || userTaskTriggerKafkaConsumer == null) break;

                      ConsumerRecords<UUID, DeserializationResult<UserTaskTriggerDTO>> records =
                          userTaskTriggerKafkaConsumer.poll(Duration.ofMillis(100));

                      for (ConsumerRecord<UUID, DeserializationResult<UserTaskTriggerDTO>> rec :
                          records) {
                        DeserializationResult<UserTaskTriggerDTO> result = rec.value();

                        if (result == null || !result.isSuccess()) {
                          handleFailedRecord(rec, result);
                          continue;
                        }

                        try {
                          userTaskTriggerConsumer.accept(result.getValue());
                        } catch (Exception e) {
                          log.error(
                              "Error processing UserTaskTrigger for processInstanceId={}: {}",
                              result.getValue().getProcessInstanceId(),
                              e.getMessage(),
                              e);
                        }
                      }
                    }
                  }
                } finally {
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
    synchronized (consumerLock) {
      if (userTaskTriggerKafkaConsumer != null) {
        userTaskTriggerKafkaConsumer.wakeup();
      }
    }
  }

  private void handleFailedRecord(
      ConsumerRecord<UUID, DeserializationResult<UserTaskTriggerDTO>> rec,
      DeserializationResult<UserTaskTriggerDTO> result) {
    String error = result != null ? result.getError() : "null deserialization result";
    log.error(
        "Deserialization/verification failed for UserTaskTrigger on topic={} partition={} offset={}: {}",
        rec.topic(),
        rec.partition(),
        rec.offset(),
        error);

    if (result != null && result.hasValue()) {
      UserTaskTriggerDTO dto = result.getValue();
      try {
        processInstanceResponder
            .responderForUserTaskTrigger(dto)
            .respondError(
                "TASK_TRIGGER_VERIFICATION_FAILED",
                "User task trigger could not be verified: " + error);
        log.warn(
            "Reported error for processInstanceId={} elementPath={} due to: {}",
            dto.getProcessInstanceId(),
            dto.getElementInstanceIdPath(),
            error);
      } catch (Exception reportEx) {
        log.warn(
            "Failed to report error for processInstanceId={}",
            dto.getProcessInstanceId(),
            reportEx);
      }
    } else {
      log.error(
          "Cannot report error: body could not be decoded for UserTaskTrigger at"
              + " topic={} partition={} offset={}. Record will be skipped.",
          rec.topic(),
          rec.partition(),
          rec.offset());
    }
  }

  private <K, V> KafkaConsumer<K, V> createConsumer() {
    String groupId = "taktx-client-user-task-trigger-consumer-" + UUID.randomUUID();
    log.info("Creating consumer for group id {}", groupId);
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId,
            TaktUUIDDeserializer.class,
            FaultTolerantUserTaskTriggerDeserializer.class,
            "latest");
    return new KafkaConsumer<>(props);
  }
}
