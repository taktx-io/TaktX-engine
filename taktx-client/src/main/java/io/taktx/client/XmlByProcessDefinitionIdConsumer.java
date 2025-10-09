/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * This class is responsible for managing the subscription to external tasks for all process
 * definitions.
 */
@Slf4j
public class XmlByProcessDefinitionIdConsumer {

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final Executor executor;
  private boolean running = false;
  private static final Path DEFINITION_XML_PATH =
      Paths.get(System.getProperty("user.home"), ".taktx", "definitions");

  public XmlByProcessDefinitionIdConsumer(
      TaktPropertiesHelper taktPropertiesHelper, Executor executor) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.executor = executor;
  }

  public void subscribeToTopic() {
    running = true;
    log.info("Starting async process to consume XML by process definition id");
    CompletableFuture.runAsync(
        () -> {
          try (KafkaConsumer<ProcessDefinitionKey, String> consumer = createConsumer()) {

            String prefixedTopicName =
                taktPropertiesHelper.getPrefixedTopicName(
                    Topics.XML_BY_PROCESS_DEFINITION_ID.getTopicName());

            log.info("Subscribing to topic " + prefixedTopicName);
            consumer.subscribe(Collections.singletonList(prefixedTopicName));

            while (running) {
              consumeRecords(consumer);
            }

            consumer.unsubscribe();
          } catch (IOException e) {
            throw new IllegalStateException(e);
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
              writeDefinition(
                  taktPropertiesHelper.getTenant(),
                  taktPropertiesHelper.getNamespace(),
                  filename,
                  xml);
            });
  }

  private void writeDefinition(String tenant, String namespace, String filename, String xml) {
    try {
      // Create directory structure for tenant and namespace if it doesn't exist
      Path tenantNamespacePath = Paths.get(DEFINITION_XML_PATH.toString(), tenant, namespace);
      if (!java.nio.file.Files.exists(tenantNamespacePath)) {
        java.nio.file.Files.createDirectories(tenantNamespacePath);
        log.info("Created directory structure: {}", tenantNamespacePath);
      }

      // Create the file path
      Path filePath = tenantNamespacePath.resolve(filename);

      // Check if the file already exists
      if (java.nio.file.Files.exists(filePath)) {
        log.debug("Process definition file already exists, overwriting: {}", filePath);
          java.nio.file.Files.delete(filePath);
      }

      // Write the XML to the file
      java.nio.file.Files.writeString(filePath, xml);
      log.info("Wrote process definition to file: {}", filePath);
    } catch (IOException e) {
      log.error("Failed to write process definition file: {}", filename, e);
    }
  }

  private <K, V> KafkaConsumer<K, V> createConsumer() throws IOException {
    Properties props =
        taktPropertiesHelper.getKafkaConsumerProperties(
            "xml-by-process-definition-id-consumer-" + UUID.randomUUID(),
            ProcessDefinitionKeyJsonDeserializer.class,
            ZippedStringDeserializer.class,
            "earliest");
    return new KafkaConsumer<>(props);
  }

  public String getProcessDefinitionXml(ProcessDefinitionKey processDefinitionKey)
      throws IOException {
    Path tenantNamespacePath =
        Paths.get(
            DEFINITION_XML_PATH.toString(),
            taktPropertiesHelper.getTenant(),
            taktPropertiesHelper.getNamespace());
    Path filePath =
        tenantNamespacePath.resolve(
            processDefinitionKey.getProcessDefinitionId()
                + "."
                + processDefinitionKey.getVersion()
                + ".bpmn");
    if (!java.nio.file.Files.exists(filePath)) {
      return null;
    }
    return java.nio.file.Files.readString(filePath);
  }

  public void stop() {
    running = false;
  }
}
