/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.pi.integration;

import io.taktx.Topics;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.ConfigurationEventDTO.ConfigurationEventType;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.engine.generic.TopologyProducer;
import io.taktx.util.TaktPropertiesHelper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Shared test helper for security integration and end-to-end tests.
 *
 * <p>Publishes a {@link ConfigurationEventDTO} to the namespaced {@code taktx-configuration} topic
 * so the engine's global config store has signing enabled with the given keys trusted.
 */
final class SecurityEndToEndHelper {

  private SecurityEndToEndHelper() {}

  /**
   * Publishes a signing-enabled {@link GlobalConfigurationDTO} to the engine's configuration topic
   * so that the engine trusts both the engine key and all provided worker keys.
   *
   * @param bootstrapServers Kafka bootstrap.servers value
   * @param namespace topic namespace prefix, e.g. {@code "default"}
   * @param engineKeyId the engine's own signing key id, e.g. {@code "test-key-1"}
   * @param extraTrustedKeyIds additional key ids to trust (worker keys, etc.)
   */
  static void publishSigningConfiguration(
      String bootstrapServers, String namespace, String engineKeyId, String... extraTrustedKeyIds) {

    Properties props = new Properties();
    props.put("bootstrap.servers", bootstrapServers);
    props.put("taktx.engine.tenant-id", "test-tenant");
    props.put("taktx.engine.namespace", namespace);

    TaktPropertiesHelper helper = new TaktPropertiesHelper(props);
    String configTopic = helper.getPrefixedTopicName(Topics.CONFIGURATION_TOPIC.getTopicName());

    List<String> allTrusted = new ArrayList<>();
    allTrusted.add(engineKeyId);
    allTrusted.addAll(Arrays.asList(extraTrustedKeyIds));

    try (KafkaProducer<String, ConfigurationEventDTO> producer =
        new KafkaProducer<>(
            props,
            new StringSerializer(),
            TopologyProducer.CONFIGURATION_EVENT_SERDE.serializer())) {

      GlobalConfigurationDTO config =
          GlobalConfigurationDTO.builder()
              .signingEnabled(true)
              .rbacEnabled(false)
              .signingKeyId(engineKeyId)
              .trustedKeyIds(allTrusted)
              .build();

      ConfigurationEventDTO event =
          ConfigurationEventDTO.builder()
              .eventType(ConfigurationEventType.CONFIGURATION_UPDATE)
              .configuration(config)
              .timestamp(Instant.now())
              .build();

      producer.send(new ProducerRecord<>(configTopic, "config", event));
      producer.flush();
    }
  }
}
