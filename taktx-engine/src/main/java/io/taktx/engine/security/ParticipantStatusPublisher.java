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
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.KafkaClientsConfig;
import io.taktx.serdes.ParticipantStatusProtoMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Publishes latest-state participant readiness status to the approved control-plane topic. */
@ApplicationScoped
@Startup
@Slf4j
public class ParticipantStatusPublisher {

  private final TaktConfiguration configuration;
  private final KafkaClientsConfig kafkaClientsConfig;
  private final EngineSecurityReadinessEvaluator readinessEvaluator;
  private KafkaProducer<String, byte[]> producer;

  public ParticipantStatusPublisher(
      TaktConfiguration configuration,
      KafkaClientsConfig kafkaClientsConfig,
      EngineSecurityReadinessEvaluator readinessEvaluator) {
    this.configuration = configuration;
    this.kafkaClientsConfig = kafkaClientsConfig;
    this.readinessEvaluator = readinessEvaluator;
  }

  ParticipantStatusPublisher(
      TaktConfiguration configuration,
      EngineSecurityReadinessEvaluator readinessEvaluator,
      KafkaProducer<String, byte[]> producer) {
    this.configuration = configuration;
    this.kafkaClientsConfig = null;
    this.readinessEvaluator = readinessEvaluator;
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

  public ParticipantStatusDTO publishCurrentStatus() {
    ParticipantStatusDTO status = readinessEvaluator.evaluateCurrentStatus();
    publish(status);
    return status;
  }

  public void publish(ParticipantStatusDTO status) {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    ProducerRecord<String, byte[]> producerRecord =
        toRecord(configuration.getPrefixed(Topics.PARTICIPANT_STATUS_TOPIC.getTopicName()), status);
    producer.send(producerRecord);
    producer.flush();
    log.info(
        "Participant status published: topic={} key={} effectiveState={} readyForDataPlane={}",
        producerRecord.topic(),
        producerRecord.key(),
        status.getEffectiveState(),
        status.isReadyForDataPlane());
  }

  static ProducerRecord<String, byte[]> toRecord(String topic, ParticipantStatusDTO status) {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    return new ProducerRecord<>(
        topic,
        status.getParticipantInstanceId(),
        ParticipantStatusProtoMapper.toProto(status).toByteArray());
  }

  @PreDestroy
  void close() {
    if (producer != null) {
      producer.close();
    }
  }
}
