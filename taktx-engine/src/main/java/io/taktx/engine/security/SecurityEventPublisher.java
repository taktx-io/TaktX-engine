/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.quarkus.runtime.Startup;
import io.taktx.Topics;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.KafkaClientsConfig;
import io.taktx.serdes.SecurityEventProtoMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Publishes append-only namespace security events to the approved control-plane topic. */
@ApplicationScoped
@Startup
@Slf4j
public class SecurityEventPublisher {

  private final TaktConfiguration configuration;
  private final KafkaClientsConfig kafkaClientsConfig;
  private KafkaProducer<String, byte[]> producer;

  @Inject
  public SecurityEventPublisher(
      TaktConfiguration configuration, KafkaClientsConfig kafkaClientsConfig) {
    this.configuration = configuration;
    this.kafkaClientsConfig = kafkaClientsConfig;
  }

  SecurityEventPublisher(TaktConfiguration configuration, KafkaProducer<String, byte[]> producer) {
    this.configuration = configuration;
    this.kafkaClientsConfig = null;
    this.producer = producer;
  }

  @PostConstruct
  void init() {
    if (producer != null) {
      return;
    }
    if (kafkaClientsConfig == null) {
      throw new IllegalStateException(
          "KafkaClientsConfig must be available when producer is not preconfigured");
    }
    producer =
        new KafkaProducer<>(
            kafkaClientsConfig.getConfig(), new StringSerializer(), new ByteArraySerializer());
  }

  public void publish(SecurityEventDTO event) {
    publish(null, event);
  }

  public void publish(String key, SecurityEventDTO event) {
    if (event == null) {
      throw new IllegalArgumentException("event must not be null");
    }
    ProducerRecord<String, byte[]> producerRecord =
        toRecord(
            configuration.getPrefixed(Topics.SECURITY_EVENTS_TOPIC.getTopicName()), key, event);
    producer.send(producerRecord);
    producer.flush();
    log.info(
        "Security event published: topic={} key={} eventType={} severity={} code={}",
        producerRecord.topic(),
        producerRecord.key(),
        event.getEventType(),
        event.getSeverity(),
        event.getCode());
  }

  static ProducerRecord<String, byte[]> toRecord(String topic, String key, SecurityEventDTO event) {
    if (event == null) {
      throw new IllegalArgumentException("event must not be null");
    }
    return new ProducerRecord<>(
        topic,
        key != null && !key.isBlank() ? key : UUID.randomUUID().toString(),
        SecurityEventProtoMapper.toProto(event).toByteArray());
  }

  @PreDestroy
  void close() {
    if (producer != null) {
      producer.close();
    }
  }
}
