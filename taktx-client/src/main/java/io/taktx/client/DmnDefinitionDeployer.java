/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.Topics;
import io.taktx.client.serdes.XmlDmnDefinitionSerializer;
import io.taktx.dto.ParsedDmnDefinitionsDTO;
import io.taktx.dto.XmlDmnDefinitionsDTO;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.xml.DmnParser;
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
 * A deployer for DMN definitions, responsible for parsing DMN XML and sending it to the {@code
 * dmn-definitions} Kafka topic.
 */
class DmnDefinitionDeployer {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(DmnDefinitionDeployer.class);

  private final TaktPropertiesHelper taktPropertiesHelper;
  private final KafkaProducer<String, XmlDmnDefinitionsDTO> xmlEmitter;

  DmnDefinitionDeployer(TaktPropertiesHelper taktPropertiesHelper) {
    this(
        taktPropertiesHelper,
        new KafkaProducer<>(
            taktPropertiesHelper.getKafkaProducerProperties(),
            new StringSerializer(),
            new XmlDmnDefinitionSerializer()));
  }

  DmnDefinitionDeployer(
      TaktPropertiesHelper taktPropertiesHelper,
      KafkaProducer<String, XmlDmnDefinitionsDTO> xmlEmitter) {
    this.taktPropertiesHelper = taktPropertiesHelper;
    this.xmlEmitter = xmlEmitter;
  }

  /**
   * Deploys a DMN XML string by parsing it and sending the raw XML to the Kafka topic.
   *
   * @param xml the DMN XML string to deploy
   * @return the {@link ParsedDmnDefinitionsDTO} containing the parsed definitions
   */
  public ParsedDmnDefinitionsDTO deployInputStream(String xml) {
    log.info("Deploying DMN XML");
    ParsedDmnDefinitionsDTO parsed = DmnParser.parse(xml);
    xmlEmitter.send(
        new ProducerRecord<>(
            taktPropertiesHelper.getPrefixedTopicName(
                Topics.DMN_DEFINITIONS_TRIGGER_TOPIC.getTopicName()),
            parsed.getDefinitionsKey().getDmnDefinitionId(),
            new XmlDmnDefinitionsDTO(xml)));
    return parsed;
  }

  /**
   * Deploys a DMN definition from a resource path (supports {@code classpath:} and {@code file:}
   * prefixes, including Ant-style wildcards).
   *
   * @param resource the resource location, e.g. {@code "classpath:dmn/discount.dmn"} or {@code
   *     "classpath:dmn/*.dmn"}
   */
  public void deployResource(String resource) {
    String trimmedResource = resource.trim();
    if (trimmedResource.startsWith("classpath:")) {
      List<URI> resources = ResourceScanner.getResources(resource);
      for (URI res : resources) {
        log.info("Deploying classpath DMN resource: {}", res.getPath());
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
            log.info("Deploying file DMN resource: {}", fileSystemResource);
            deployInputStream(new String(is.readAllBytes()));
          }
        }
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
