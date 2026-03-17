/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.config;

import io.taktx.util.TopicSegmentValidator;
import jakarta.annotation.PostConstruct;
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

  @ConfigProperty(name = "taktx.engine.tenant-id")
  String tenantId;

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

  @ConfigProperty(name = "taktx.signing.identity-source")
  Optional<String> sharedSigningIdentitySource;

  @ConfigProperty(name = "taktx.signing.file.key-id-path")
  Optional<String> signingFileKeyIdPath;

  @ConfigProperty(name = "taktx.signing.file.private-key-path")
  Optional<String> signingFilePrivateKeyPath;

  @ConfigProperty(name = "taktx.signing.file.public-key-path")
  Optional<String> signingFilePublicKeyPath;

  @ConfigProperty(name = "taktx.signing.file.refresh-interval-ms", defaultValue = "1000")
  long signingFileRefreshIntervalMs;

  public boolean inTestMode() {
    return Boolean.parseBoolean(isTest);
  }

  @PostConstruct
  void validateSegments() {
    TopicSegmentValidator.validate("tenant-id", tenantId);
    TopicSegmentValidator.validate("namespace", namespace);
  }

  /** Returns the fully-qualified topic/store name. Format: {@code <tenantId>.<namespace>.<name>} */
  public String getPrefixed(String name) {
    String prefixedName = tenantId + "." + namespace + "." + name;
    if (prefixedName.length() > 254) {
      throw new IllegalArgumentException("Topic name is too long: " + prefixedName);
    }
    return prefixedName;
  }

  public Path getLicenseFilePath() {
    return "-".equals(licenseFileLocation) ? LICENSE_PATH : Path.of(licenseFileLocation);
  }

  public String getSigningIdentitySourceType() {
    String sharedSource = normalized(sharedSigningIdentitySource);
    if (sharedSource != null) {
      return sharedSource;
    }
    return "generated";
  }

  public String getSigningFileKeyIdPath() {
    return normalized(signingFileKeyIdPath);
  }

  public String getSigningFilePrivateKeyPath() {
    return normalized(signingFilePrivateKeyPath);
  }

  public String getSigningFilePublicKeyPath() {
    return normalized(signingFilePublicKeyPath);
  }

  private static String normalized(Optional<String> value) {
    return value.map(String::trim).filter(v -> !v.isBlank()).orElse(null);
  }

  public boolean getTopicCreationEnabled() {
    return Boolean.parseBoolean(topicCreationEnabled);
  }
}
