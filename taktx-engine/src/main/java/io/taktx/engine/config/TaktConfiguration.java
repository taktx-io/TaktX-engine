/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.config;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Getter
public class TaktConfiguration {
  private static final Path LICENSE_PATH =
      Paths.get(System.getProperty("user.home"), ".taktx", "license.lic");

  @ConfigProperty(name = "taktx.engine.namespace")
  String namespace;

  @ConfigProperty(name = "taktx.engine.keyvaluestoretype")
  String supplierType;

  @ConfigProperty(name = "taktx.engine.topic.replication-factor")
  short replicationFactor;

  @ConfigProperty(name = "taktx.engine.topic.partitions")
  int partitions;

  @ConfigProperty(name = "kafka.bootstrap.servers")
  String bootstrapServers;

  @ConfigProperty(name = "taktx.test", defaultValue = "false")
  String isTest;

  @ConfigProperty(name = "taktx.engine.host", defaultValue = "false")
  String host;

  @ConfigProperty(name = "taktx.engine.port", defaultValue = "8080")
  int port;

  @ConfigProperty(name = "taktx.engine.topicCreationEnabled", defaultValue = "true")
  String topicCreationEnabled;

  @ConfigProperty(name = "taktx.engine.broadcastInstanceUpdates", defaultValue = "true")
  String broadcastInstanceUpdates;

  @ConfigProperty(name = "taktx.engine.licenseFileLocation", defaultValue = "-")
  String licenseFileLocation;

  // ── Security: command authorization ──────────────────────────────────────────
  @ConfigProperty(name = "taktx.security.authorization.enabled", defaultValue = "false")
  boolean authorizationEnabled;

  @ConfigProperty(name = "taktx.security.authorization.reject-expired", defaultValue = "false")
  boolean rejectExpiredTokens;

  @ConfigProperty(name = "taktx.security.authorization.nonce-check.enabled", defaultValue = "true")
  boolean nonceCheckEnabled;

  @ConfigProperty(name = "taktx.platform.public-key")
  Optional<String> platformPublicKeyBase64;

  // ── Security: engine-internal message signing ────────────────────────────────
  @ConfigProperty(name = "taktx.security.signing.enabled", defaultValue = "false")
  boolean signingEnabled;

  public boolean inTestMode() {
    return Boolean.parseBoolean(isTest);
  }

  public String getPrefixed(String name) {
    String prefixedName = namespace + "." + name;
    if (prefixedName.length() > 254) {
      throw new IllegalArgumentException("Topic name is too long: " + prefixedName);
    }
    return prefixedName;
  }

  public Path getLicenseFilePath() {
    return "-".equals(licenseFileLocation) ? LICENSE_PATH : Path.of(licenseFileLocation);
  }

  public boolean getTopicCreationEnabled() {
    return Boolean.parseBoolean(topicCreationEnabled);
  }
}
