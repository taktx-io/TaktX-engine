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
import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.SecurityMode;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.generic.KafkaClientsConfig;
import io.taktx.serdes.NamespaceSecurityPolicyProtoMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Publishes the engine's startup-resolved namespace security mode to the compacted {@code
 * taktx-security-policy} topic once at startup.
 *
 * <p>The mode is read-only after this point — it is determined by the presence of {@code
 * TAKTX_PLATFORM_PUBLIC_KEY} and never changes at runtime. The record is keyed with {@code
 * "policy"} so the compacted topic retains one entry per namespace for Console display.
 *
 * <p>This publish is best-effort; failure is logged as a warning but does not block startup.
 */
@ApplicationScoped
@Startup
@Slf4j
public class NamespacePosturePublisher {

  static final String POLICY_RECORD_KEY = "policy";

  private final TaktConfiguration configuration;
  private final KafkaClientsConfig kafkaClientsConfig;

  @Inject
  public NamespacePosturePublisher(
      TaktConfiguration configuration, KafkaClientsConfig kafkaClientsConfig) {
    this.configuration = configuration;
    this.kafkaClientsConfig = kafkaClientsConfig;
  }

  @PostConstruct
  void publishStartupPosture() {
    SecurityMode mode = configuration.isAnchored() ? SecurityMode.ANCHORED : SecurityMode.OPEN;
    String topic = configuration.getPrefixed(Topics.SECURITY_POLICY_TOPIC.getTopicName());
    try {
      Map<String, Object> producerConfig = new HashMap<>(kafkaClientsConfig.getConfig());
      producerConfig.put("max.block.ms", "5000");
      producerConfig.put("delivery.timeout.ms", "5000");
      producerConfig.put("request.timeout.ms", "3000");
      try (KafkaProducer<String, byte[]> producer =
          new KafkaProducer<>(producerConfig, new StringSerializer(), new ByteArraySerializer())) {
        byte[] value =
            NamespaceSecurityPolicyProtoMapper.toProto(
                    NamespaceSecurityPolicyDTO.builder().mode(mode).build())
                .toByteArray();
        producer.send(new ProducerRecord<>(topic, POLICY_RECORD_KEY, value));
        producer.flush();
        log.info("✅ Namespace posture published: topic={} mode={}", topic, mode);
      }
    } catch (Exception e) {
      log.warn(
          "Failed to publish namespace posture (non-fatal): topic={} mode={} error={}",
          topic,
          mode,
          e.getMessage());
    }
  }
}
