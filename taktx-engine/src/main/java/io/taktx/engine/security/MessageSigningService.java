/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.Stores;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EnvironmentVariableKeyProvider;
import io.taktx.security.SigningException;
import io.taktx.security.SigningKeyProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

/**
 * Signs engine-internal Kafka messages with Ed25519.
 *
 * <p>When signing is enabled, adds the {@code X-TaktX-Signature} header: {@code
 * "<keyId>.<base64(signature)>"} to every internally-generated record before forwarding.
 */
@ApplicationScoped
@Slf4j
public class MessageSigningService {

  private static final String HEADER_NAME = "X-TaktX-Signature";
  private static final String CONFIG_KEY = "config";

  private final TaktConfiguration config;
  private final SigningKeyProvider keyProvider;
  private final KafkaStreams kafkaStreams;

  private ReadOnlyKeyValueStore<String, io.taktx.dto.ConfigurationEventDTO> configStore;

  @Inject
  public MessageSigningService(TaktConfiguration config, KafkaStreams kafkaStreams) {
    this.config = config;
    this.kafkaStreams = kafkaStreams;
    this.keyProvider = new EnvironmentVariableKeyProvider();
  }

  /** Package-private constructor for unit tests — allows injecting a custom key provider. */
  MessageSigningService(
      TaktConfiguration config, KafkaStreams kafkaStreams, SigningKeyProvider keyProvider) {
    this.config = config;
    this.kafkaStreams = kafkaStreams;
    this.keyProvider = keyProvider;
  }

  /**
   * Adds {@code X-TaktX-Signature} header to {@code headers} if signing is enabled.
   *
   * @param headers the Kafka record headers to mutate
   * @param payloadBytes the serialised value bytes that will be signed
   */
  public void signIfEnabled(Headers headers, byte[] payloadBytes) {
    if (!config.isSigningEnabled()) return;

    io.taktx.dto.GlobalConfigurationDTO globalConfig = getGlobalConfig();
    if (globalConfig == null || !globalConfig.isSigningEnabled()) return;

    List<String> activeKeyIds = globalConfig.getActiveKeyIds();
    if (activeKeyIds == null || activeKeyIds.isEmpty()) {
      log.warn("Signing is enabled but no active key IDs configured");
      return;
    }

    String keyId = activeKeyIds.stream().filter(keyProvider::hasKey).findFirst().orElse(null);

    if (keyId == null) {
      log.error(
          "Signing enabled but no private key available for any active key ID: {}", activeKeyIds);
      return;
    }

    try {
      String privateKey = keyProvider.getPrivateKey(keyId);
      byte[] signature = Ed25519Service.sign(payloadBytes, privateKey);
      String headerValue = keyId + "." + Base64.getEncoder().encodeToString(signature);
      headers.add(HEADER_NAME, headerValue.getBytes(StandardCharsets.UTF_8));
      log.trace("Added {} header with keyId={}", HEADER_NAME, keyId);
    } catch (SigningException e) {
      log.error("Failed to sign message: {}", e.getMessage(), e);
    }
  }

  private io.taktx.dto.GlobalConfigurationDTO getGlobalConfig() {
    try {
      if (configStore == null) {
        configStore =
            kafkaStreams.store(
                StoreQueryParameters.fromNameAndType(
                    config.getPrefixed(Stores.GLOBAL_CONFIGURATION.getStorename()),
                    QueryableStoreTypes.keyValueStore()));
      }
      io.taktx.dto.ConfigurationEventDTO event = configStore.get(CONFIG_KEY);
      return event != null ? event.getConfiguration() : null;
    } catch (Exception e) {
      log.debug("Could not read global configuration store: {}", e.getMessage());
      return null;
    }
  }
}
