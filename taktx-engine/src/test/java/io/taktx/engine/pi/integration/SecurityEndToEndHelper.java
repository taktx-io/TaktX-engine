/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.pi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.taktx.Topics;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.dto.ConfigurationEventDTO.ConfigurationEventType;
import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.util.TaktPropertiesHelper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Shared test helper for security integration and end-to-end tests.
 *
 * <p>Publishes a {@link ConfigurationEventDTO} to the namespaced {@code taktx-configuration} topic
 * so the engine's global config store enables signing/authorization with the given keys trusted.
 */
final class SecurityEndToEndHelper {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

  private SecurityEndToEndHelper() {}

  /**
   * Publishes a security-enabled {@link GlobalConfigurationDTO} to the engine's configuration
   * topic.
   *
   * @param bootstrapServers Kafka bootstrap.servers value
   * @param namespace topic namespace prefix, e.g. {@code "default"}
   * @param extraTrustedKeyIds additional key ids to trust (worker keys, etc.)
   */
  static void publishSigningConfiguration(
      String bootstrapServers, String namespace, String... extraTrustedKeyIds) {

    Properties props = new Properties();
    props.put("bootstrap.servers", bootstrapServers);
    props.put("taktx.engine.tenant-id", "test-tenant");
    props.put("taktx.engine.namespace", namespace);

    TaktPropertiesHelper helper = new TaktPropertiesHelper(props);
    String configTopic = helper.getPrefixedTopicName(Topics.CONFIGURATION_TOPIC.getTopicName());

    List<String> allTrusted = new ArrayList<>();
    allTrusted.addAll(Arrays.asList(extraTrustedKeyIds));

    try (KafkaProducer<String, byte[]> producer =
        new KafkaProducer<>(props, new StringSerializer(), new ByteArraySerializer())) {

      GlobalConfigurationDTO config =
          GlobalConfigurationDTO.builder()
              .signingEnabled(true)
              .engineRequiresAuthorization(true)
              .trustedKeyIds(allTrusted)
              .build();

      ConfigurationEventDTO event =
          ConfigurationEventDTO.builder()
              .eventType(ConfigurationEventType.CONFIGURATION_UPDATE)
              .configuration(config)
              .timestamp(Instant.now())
              .build();

      producer.send(
          new ProducerRecord<>(configTopic, "config", OBJECT_MAPPER.writeValueAsBytes(event)));
      producer.flush();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to publish security configuration", e);
    }
  }
}
