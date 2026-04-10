/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.ProcessDefinitionKeyJsonDeserializer;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.serdes.ZippedStringDeserializer;
import io.taktx.util.TaktPropertiesHelper;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;

/**
 * This class is responsible for managing the subscription to external tasks for all process
 * definitions.
 */
public class XmlByProcessDefinitionIdConsumer {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(XmlByProcessDefinitionIdConsumer.class);
  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private volatile boolean running = false;
  private volatile KafkaConsumer<ProcessDefinitionKey, String> activeConsumer;
  private static final Path DEFINITION_XML_PATH =
      Paths.get(System.getProperty("user.home"), ".taktx", "definitions");

  /**
   * Constructor for XmlByProcessDefinitionIdConsumer.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   * @param executor the Executor to use for asynchronous processing
   */
  public XmlByProcessDefinitionIdConsumer(
      TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  /**
   * Subscribes to the topic for XML by process definition ID and starts consuming messages
   * asynchronously.
   */
  public void subscribeToTopic() {
    running = true;
    log.info("Starting async process to consume XML by process definition id");
    CompletableFuture.runAsync(
        () -> {
          try (KafkaConsumer<ProcessDefinitionKey, String> consumer = createConsumer()) {
            activeConsumer = consumer;

            String prefixedTopicName =
                taktPropertiesHelper.getPrefixedTopicName(
                    Topics.XML_BY_PROCESS_DEFINITION_ID.getTopicName());

            log.info("Subscribing to topic {}", prefixedTopicName);
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

  private void consumeRecords(KafkaConsumer<ProcessDefinitionKey, String> consumer) {
    consumer
        .poll(java.time.Duration.ofMillis(100))
        .forEach(
            instanceUpdateRecord -> {
              ProcessDefinitionKey key = instanceUpdateRecord.key();
              String xml = instanceUpdateRecord.value();
              String filename = key.getProcessDefinitionId() + "." + key.getVersion() + ".bpmn";
              log.info("Consume XML definition for {} and store to {}", key, filename);
              writeDefinition(taktPropertiesHelper.getNamespace(), filename, xml);
            });
  }

  private void writeDefinition(String namespace, String filename, String xml) {
    try {
      // Create directory structure for namespace if it doesn't exist
      Path namespacePath = Paths.get(DEFINITION_XML_PATH.toString(), namespace);
      if (!java.nio.file.Files.exists(namespacePath)) {
        java.nio.file.Files.createDirectories(namespacePath);
        log.info("Created directory structure: {}", namespacePath);
      }

      // Create the file path
      Path filePath = namespacePath.resolve(filename);

      // Always write the file: Files.writeString truncates-and-overwrites atomically.
      // This handles a fresh engine installation where the state store is wiped and
      // version numbers restart — the same (id, version) may arrive with different content.
      java.nio.file.Files.writeString(filePath, xml);
      log.info("Wrote process definition to file: {}", filePath);
    } catch (IOException e) {
      log.error("Failed to write process definition file: {}", filename, e);
    }
  }

  private <K, V> KafkaConsumer<K, V> createConsumer() {
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            "xml-by-process-definition-id-consumer-" + UUID.randomUUID(),
            ProcessDefinitionKeyJsonDeserializer.class,
            ZippedStringDeserializer.class,
            "earliest");
    return new KafkaConsumer<>(props);
  }

  /**
   * Retrieves the process definition XML for the given process definition key.
   *
   * @param processDefinitionKey the key of the process definition
   * @return the XML content as a String, or null if not found
   * @throws IOException if an I/O error occurs reading from the file
   */
  public String getProcessDefinitionXml(ProcessDefinitionKey processDefinitionKey)
      throws IOException {
    Path namespacePath =
        Paths.get(DEFINITION_XML_PATH.toString(), taktPropertiesHelper.getNamespace());
    Path filePath =
        namespacePath.resolve(
            processDefinitionKey.getProcessDefinitionId()
                + "."
                + processDefinitionKey.getVersion()
                + ".bpmn");
    if (!java.nio.file.Files.exists(filePath)) {
      return null;
    }
    return java.nio.file.Files.readString(filePath);
  }

  /** Stops the consumer from processing further records. */
  public void stop() {
    running = false;
    KafkaConsumer<ProcessDefinitionKey, String> c = activeConsumer;
    if (c != null) {
      c.wakeup();
    }
  }
}
