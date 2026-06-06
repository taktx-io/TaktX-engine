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
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventSeverity;
import io.taktx.dto.SecurityEventType;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.KafkaClientsConfig;
import io.taktx.serdes.ParticipantStatusProtoMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
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

  static final String DATA_PLANE_BLOCKED_CODE = "DATA_PLANE_BLOCKED";

  private final TaktConfiguration configuration;
  private final KafkaClientsConfig kafkaClientsConfig;
  private final EngineSecurityReadinessEvaluator readinessEvaluator;
  private final SecurityEventPublisher securityEventPublisher;
  private final AtomicReference<String> lastBlockedEventFingerprint = new AtomicReference<>(null);
  private KafkaProducer<String, byte[]> producer;

  @Inject
  public ParticipantStatusPublisher(
      TaktConfiguration configuration,
      KafkaClientsConfig kafkaClientsConfig,
      SecurityEventPublisher securityEventPublisher,
      EngineSecurityReadinessEvaluator readinessEvaluator) {
    this.configuration = configuration;
    this.kafkaClientsConfig = kafkaClientsConfig;
    this.securityEventPublisher = securityEventPublisher;
    this.readinessEvaluator = readinessEvaluator;
  }

  ParticipantStatusPublisher(
      TaktConfiguration configuration,
      EngineSecurityReadinessEvaluator readinessEvaluator,
      SecurityEventPublisher securityEventPublisher,
      KafkaProducer<String, byte[]> producer) {
    this.configuration = configuration;
    this.kafkaClientsConfig = null;
    this.readinessEvaluator = readinessEvaluator;
    this.securityEventPublisher = securityEventPublisher;
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
    publishBlockedEventIfNeeded(status);
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

  private void publishBlockedEventIfNeeded(ParticipantStatusDTO status) {
    String code = null;
    String message = null;
    Map<String, String> metadata = new LinkedHashMap<>();

    if (status != null && !status.isReadyForDataPlane()) {
      code =
          status.getMismatchReasons().isEmpty()
              ? DATA_PLANE_BLOCKED_CODE
              : status.getMismatchReasons().getFirst().getCode();
      message =
          status.getMismatchReasons().isEmpty()
              ? "Protected data-plane participation remains blocked because the participant is not READY"
              : status.getMismatchReasons().getFirst().getMessage();
      metadata.put("effectiveState", String.valueOf(status.getEffectiveState()));
      metadata.put(
          "mismatchCodes",
          status.getMismatchReasons().stream()
              .map(reason -> reason.getCode())
              .filter(Objects::nonNull)
              .distinct()
              .reduce((left, right) -> left + "," + right)
              .orElse(""));
    }

    if (code == null || securityEventPublisher == null || status == null) {
      lastBlockedEventFingerprint.set(null);
      return;
    }

    metadata.put("participantId", status.getParticipantId());
    metadata.put("participantInstanceId", status.getParticipantInstanceId());
    metadata.put("readyForDataPlane", Boolean.toString(status.isReadyForDataPlane()));

    String fingerprint =
        code
            + "|"
            + metadata.getOrDefault("effectiveState", "")
            + "|"
            + metadata.getOrDefault("mismatchCodes", "");
    if (fingerprint.equals(lastBlockedEventFingerprint.get())) {
      return;
    }

    securityEventPublisher.publish(
        SecurityEventDTO.builder()
            .eventType(SecurityEventType.DATA_PLANE_BLOCKED)
            .severity(SecurityEventSeverity.WARNING)
            .occurredAtMs(status.getLastSeenAt())
            .namespace(status.getNamespace())
            .participantId(status.getParticipantId())
            .participantInstanceId(status.getParticipantInstanceId())
            .code(code)
            .message(message)
            .metadata(Map.copyOf(metadata))
            .build());
    lastBlockedEventFingerprint.set(fingerprint);
  }

  @PreDestroy
  void close() {
    if (producer != null) {
      producer.close();
    }
  }
}
