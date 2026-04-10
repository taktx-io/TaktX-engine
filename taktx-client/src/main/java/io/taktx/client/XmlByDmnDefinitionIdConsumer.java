/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.DmnDefinitionKeyJsonDeserializer;
import io.taktx.dto.DmnDefinitionKey;
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
 * Subscribes to the {@code xml-by-dmn-definition-id} Kafka topic and persists each DMN definition
 * XML to the local file system under {@code ~/.taktx/definitions/<namespace>/}.
 */
public class XmlByDmnDefinitionIdConsumer {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(XmlByDmnDefinitionIdConsumer.class);
  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private volatile boolean running = false;
  private volatile KafkaConsumer<DmnDefinitionKey, String> activeConsumer;
  private static final Path DEFINITION_XML_PATH =
      Paths.get(System.getProperty("user.home"), ".taktx", "definitions");

  /**
   * Constructor for XmlByDmnDefinitionIdConsumer.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   * @param executor the Executor to use for asynchronous processing
   */
  public XmlByDmnDefinitionIdConsumer(
      TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  /**
   * Subscribes to the topic for XML by DMN definition ID and starts consuming messages
   * asynchronously.
   */
  public void subscribeToTopic() {
    running = true;
    log.info("Starting async process to consume XML by DMN definition id");
    CompletableFuture.runAsync(
        () -> {
          try (KafkaConsumer<DmnDefinitionKey, String> consumer = createConsumer()) {
            activeConsumer = consumer;

            String prefixedTopicName =
                taktPropertiesHelper.getPrefixedTopicName(
                    Topics.XML_BY_DMN_DEFINITION_ID.getTopicName());

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

  private void consumeRecords(KafkaConsumer<DmnDefinitionKey, String> consumer) {
    consumer
        .poll(java.time.Duration.ofMillis(100))
        .forEach(
            dmnRecord -> {
              DmnDefinitionKey key = dmnRecord.key();
              String xml = dmnRecord.value();
              String filename = key.getDmnDefinitionId() + "." + key.getVersion() + ".dmn";
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
      log.info("Wrote DMN definition to file: {}", filePath);
    } catch (IOException e) {
      log.error("Failed to write DMN definition file: {}", filename, e);
    }
  }

  private <K, V> KafkaConsumer<K, V> createConsumer() {
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            "xml-by-dmn-definition-id-consumer-" + UUID.randomUUID(),
            DmnDefinitionKeyJsonDeserializer.class,
            ZippedStringDeserializer.class,
            "earliest");
    return new KafkaConsumer<>(props);
  }

  /**
   * Retrieves the DMN definition XML for the given DMN definition key.
   *
   * @param dmnDefinitionKey the key of the DMN definition
   * @return the XML content as a String, or null if not found
   * @throws IOException if an I/O error occurs reading from the file
   */
  public String getDmnDefinitionXml(DmnDefinitionKey dmnDefinitionKey) throws IOException {
    Path namespacePath =
        Paths.get(DEFINITION_XML_PATH.toString(), taktPropertiesHelper.getNamespace());
    Path filePath =
        namespacePath.resolve(
            dmnDefinitionKey.getDmnDefinitionId() + "." + dmnDefinitionKey.getVersion() + ".dmn");
    if (!java.nio.file.Files.exists(filePath)) {
      return null;
    }
    return java.nio.file.Files.readString(filePath);
  }

  /** Stops the consumer from processing further records. */
  public void stop() {
    running = false;
    KafkaConsumer<DmnDefinitionKey, String> c = activeConsumer;
    if (c != null) {
      c.wakeup();
    }
  }
}
