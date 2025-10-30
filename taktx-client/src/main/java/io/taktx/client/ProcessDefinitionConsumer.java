/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.ProcessDefinitionJsonDeserializer;
import io.taktx.client.serdes.ProcessDefinitionKeyJsonDeserializer;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.util.TaktPropertiesHelper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;

/**
 * A consumer that subscribes to process definition activation records from a Kafka topic, maintains
 * a map of deployed process definitions, and notifies registered consumers of updates.
 */
public class ProcessDefinitionConsumer {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ProcessDefinitionConsumer.class);
  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private final Map<ProcessDefinitionKey, String> storedHashes = new ConcurrentHashMap<>();
  private final Map<ProcessDefinitionKey, ProcessDefinitionDTO> definitionMap =
      new ConcurrentHashMap<>();
  private final List<BiConsumer<ProcessDefinitionKey, ProcessDefinitionDTO>>
      processDefinitionUpdateConsumers = new ArrayList<>();
  private KafkaConsumer<ProcessDefinitionKey, ProcessDefinitionDTO> definitionActivationConsumer;

  private volatile boolean running = false;

  /**
   * Constructor for ProcessDefinitionConsumer.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   * @param executor the Executor to use for asynchronous processing
   */
  ProcessDefinitionConsumer(TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  /**
   * Subscribes to the process definition activation topic and starts processing records. This
   * method creates a Kafka consumer, subscribes to the appropriate topic, and processes incoming
   * records asynchronously. It updates the internal map of deployed process definitions and
   * notifies registered consumers of any updates.
   */
  public void subscribeToDefinitionRecords() {
    definitionActivationConsumer = createConsumer();

    String prefixedTopicName =
        taktPropertiesHelper.getPrefixedTopicName(
            Topics.PROCESS_DEFINITION_ACTIVATION_TOPIC.getTopicName());

    log.info("Subscribing to topic {}", prefixedTopicName);
    subscribe(prefixedTopicName);

    running = true;

    CompletableFuture.runAsync(
        () -> {
          while (running) {
            ConsumerRecords<ProcessDefinitionKey, ProcessDefinitionDTO> records =
                definitionActivationConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<ProcessDefinitionKey, ProcessDefinitionDTO> activationRecord :
                records) {
              log.info(
                  "Received definition activation record {} to state {}",
                  activationRecord.key(),
                  activationRecord.value().getState());
              if (activationRecord.key().getVersion() == -1) {
                continue;
              }
              final String storedHash = storedHashes.get(activationRecord.key());
              if (storedHash != null
                  && !storedHash.equals(
                      activationRecord.value().getDefinitions().getDefinitionsKey().getHash())) {
                log.warn(
                    "Hash mismatch for process definition {} {}",
                    activationRecord.key(),
                    activationRecord.value());
              }

              storedHashes.put(
                  activationRecord.key(),
                  activationRecord.value().getDefinitions().getDefinitionsKey().getHash());

              definitionMap.put(activationRecord.key(), activationRecord.value());

              log.info(
                  "Notifying {} consumers of process definition update",
                  processDefinitionUpdateConsumers.size());
              processDefinitionUpdateConsumers.forEach(
                  consumer -> consumer.accept(activationRecord.key(), activationRecord.value()));
            }
          }

          definitionActivationConsumer.unsubscribe();
          definitionActivationConsumer.close();
        },
        executor);
  }

  /**
   * Stops the consumer from processing further records. This method sets the running flag to false,
   * which will cause the processing loop to exit and the consumer to unsubscribe and close.
   */
  public void stop() {
    running = false;
  }

  /**
   * Retrieves the map of deployed process definitions.
   *
   * @return a map of ProcessDefinitionKey to ProcessDefinitionDTO representing the deployed process
   *     definitions
   */
  public Map<ProcessDefinitionKey, ProcessDefinitionDTO> getDeployedProcessDefinitions() {
    return definitionMap;
  }

  /**
   * Retrieves the deployed process definitions for a specific process definition ID.
   *
   * @param processDefinitionId the process definition ID to filter by
   * @return a map of ProcessDefinitionKey to ProcessDefinitionDTO for the specified process
   *     definition ID
   */
  public Map<ProcessDefinitionKey, ProcessDefinitionDTO> getDeployedProcessDefinitions(
      String processDefinitionId) {
    return definitionMap.entrySet().stream()
        .filter(e -> e.getKey().getProcessDefinitionId().equals(processDefinitionId))
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  /**
   * Retrieves a deployed process definition by its process definition ID and hash.
   *
   * @param processDefinitionId the process definition ID to filter by
   * @param hash the hash to filter by
   * @return an Optional containing the ProcessDefinitionDTO if found, or empty if not found
   */
  public Optional<ProcessDefinitionDTO> getDeployedProcessDefinitionbyHash(
      String processDefinitionId, String hash) {
    return definitionMap.entrySet().stream()
        .filter(
            e ->
                e.getKey().getProcessDefinitionId().equals(processDefinitionId)
                    && e.getValue().getDefinitions().getDefinitionsKey().getHash().equals(hash))
        .map(Entry::getValue)
        .findFirst();
  }

  /**
   * Registers a consumer to be notified of process definition updates.
   *
   * @param consumer a BiConsumer that accepts a ProcessDefinitionKey and ProcessDefinitionDTO to be
   *     notified of updates
   */
  public void registerProcessDefinitionUpdateConsumer(
      BiConsumer<ProcessDefinitionKey, ProcessDefinitionDTO> consumer) {
    processDefinitionUpdateConsumers.add(consumer);
  }

  /**
   * Unsubscribes from the process definition activation topic. This method causes the consumer to
   * unsubscribe from the topic and stop receiving records.
   */
  private void subscribe(String prefixedTopicName) {
    definitionActivationConsumer.subscribe(Collections.singletonList(prefixedTopicName));
  }

  /**
   * Creates a Kafka consumer for process definition activation records.
   *
   * @return a KafkaConsumer configured for ProcessDefinitionKey and ProcessDefinitionDTO
   */
  private <K, V> KafkaConsumer<K, V> createConsumer() {
    String groupId = "client-definition-activation-consumer-" + UUID.randomUUID();

    log.info("Creating consumer for group id {}", groupId);
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            groupId,
            ProcessDefinitionKeyJsonDeserializer.class,
            ProcessDefinitionJsonDeserializer.class,
            "earliest");
    return new KafkaConsumer<>(props);
  }
}
