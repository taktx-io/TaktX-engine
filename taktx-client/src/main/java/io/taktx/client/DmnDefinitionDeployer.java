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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;

/**
 * A deployer for DMN definitions, responsible for parsing DMN XML and sending it to the
 * {@code dmn-definitions} Kafka topic.
 */
class DmnDefinitionDeployer {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(DmnDefinitionDeployer.class);

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
}
