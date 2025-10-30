/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.XmlDefinitionSerializer;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.XmlDefinitionsDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.xml.BpmnParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;

/**
 * A deployer for process definitions, responsible for parsing BPMN XML and sending the parsed
 * definitions to a Kafka topic.
 */
class ProcessDefinitionDeployer {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ProcessDefinitionDeployer.class);
  private final TaktPropertiesHelper taktPropertiesHelper;
  private final KafkaProducer<String, XmlDefinitionsDTO> xmlEmitter;

  /**
   * Constructor for ProcessDefinitionDeployer that creates a KafkaProducer from properties.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   */
  ProcessDefinitionDeployer(TaktPropertiesHelper taktPropertiesHelper) {
    this(
        taktPropertiesHelper,
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(
                StringSerializer.class, XmlDefinitionSerializer.class)));
  }

  /**
   * Constructor that accepts an externally provided KafkaProducer. This is intended for tests and
   * advanced usage where the producer lifecycle is managed outside this class.
   *
   * @param taktPropertiesHelper the TaktPropertiesHelper to use for configuration
   * @param xmlEmitter an externally created KafkaProducer (may be a mock in tests)
   */
  ProcessDefinitionDeployer(
      TaktPropertiesHelper taktPropertiesHelper,
      KafkaProducer<String, XmlDefinitionsDTO> xmlEmitter) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.xmlEmitter = xmlEmitter;
  }

  /**
   * Deploys a BPMN XML string by parsing it and sending the parsed definitions to a Kafka topic.
   *
   * @param xml the BPMN XML string to deploy
   * @return the ParsedDefinitionsDTO containing the parsed definitions
   */
  public ParsedDefinitionsDTO deployInputStream(String xml) {
    log.info("Deploying XML {}", xml);
    ParsedDefinitionsDTO definitions = BpmnParser.parse(xml);
    xmlEmitter.send(
        new ProducerRecord<>(
            taktPropertiesHelper.getPrefixedTopicName(
                Topics.PROCESS_DEFINITIONS_TRIGGER_TOPIC.getTopicName()),
            definitions.getDefinitionsKey().getProcessDefinitionId(),
            new XmlDefinitionsDTO(xml)));
    return definitions;
  }

  public void deployResource(String resource) {
    String trimmedResource = resource.trim();
    if (trimmedResource.startsWith("classpath:")) {
      List<URI> resources = ResourceScanner.getResources(resource);
      for (URI res : resources) {
        log.info("Deploying classpath resource: {}", res.getPath());
        // Read the contants of the resource pointed at by the URI
        try (InputStream is = res.toURL().openStream()) {
          deployInputStream(new String(is.readAllBytes()));
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }
    } else if (trimmedResource.startsWith("file:")) {
      try {
        List<Path> fileSystemResources = ResourceScanner.getFileSystemResources(resource);
        for (Path fileSystemResource : fileSystemResources) {
          try (InputStream is = Files.newInputStream(fileSystemResource)) {
            log.info("Deploying file resource: {}", fileSystemResource);
            deployInputStream(new String(is.readAllBytes()));
          }
        }
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
