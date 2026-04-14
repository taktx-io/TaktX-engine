/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.config;

import io.taktx.dto.DmnValidationMode;
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

  @ConfigProperty(name = "taktx.engine.dmn.validation-mode", defaultValue = "PERMISSIVE")
  String dmnValidationMode;

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

  /**
   * Optional platform RSA public key (base64-encoded DER X.509) used as the root-of-trust anchor in
   * <em>anchored mode</em>.
   *
   * <p>Set via {@code TAKTX_PLATFORM_PUBLIC_KEY} environment variable. When present, {@link
   * io.taktx.engine.security.KeyTrustPolicyProducer} activates {@link
   * io.taktx.security.AnchoredKeyTrustPolicy}, which requires every key in {@code
   * taktx-signing-keys} to carry a valid RSA countersignature from the platform.
   *
   * <p>When absent, {@link io.taktx.security.OpenKeyTrustPolicy} is used (community mode).
   */
  @ConfigProperty(name = "taktx.platform.public-key")
  Optional<String> platformPublicKey;

  /**
   * Optional base64-encoded RSA/SHA-256 registration signature for the engine's own signing key.
   *
   * <p>Set via {@code TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE} environment variable. Required when
   * {@code TAKTX_PLATFORM_PUBLIC_KEY} is set and the engine uses a stable key source ({@code env}
   * or {@code file}). Injected into the {@link io.taktx.dto.SigningKeyDTO} published to {@code
   * taktx-signing-keys} so the engine's own key passes {@link
   * io.taktx.security.AnchoredKeyTrustPolicy} verification.
   *
   * <p>Generate this value with {@code scripts/generate_trust_anchor.sh}. The canonical payload
   * format is: {@code keyId|publicKeyBase64|algorithm|owner|role} (pipe-delimited UTF-8), signed
   * with the platform root private key using {@code SHA256withRSA}, then base64-encoded.
   *
   * <p><strong>Note:</strong> When using the {@code generated} signing source, the engine key
   * changes on every restart, making pre-signing impossible. Anchored mode therefore requires the
   * engine to use {@code file} or {@code env} source so the key is stable.
   */
  @ConfigProperty(name = "taktx.engine.key-registration-signature")
  Optional<String> engineKeyRegistrationSignature;

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

  /** Returns the platform RSA public key (base64 DER), or {@code null} if not configured. */
  public String getPlatformPublicKey() {
    return normalized(platformPublicKey);
  }

  /**
   * Returns the engine key registration signature (base64 RSA/SHA-256), or {@code null} if not
   * configured.
   */
  public String getEngineKeyRegistrationSignature() {
    return normalized(engineKeyRegistrationSignature);
  }

  private static String normalized(Optional<String> value) {
    return value.map(String::trim).filter(v -> !v.isBlank()).orElse(null);
  }

  public boolean getTopicCreationEnabled() {
    return Boolean.parseBoolean(topicCreationEnabled);
  }

  public DmnValidationMode getDmnValidationMode() {
    return parseDmnValidationMode(dmnValidationMode);
  }

  static DmnValidationMode parseDmnValidationMode(String value) {
    if (value == null || value.isBlank()) {
      return DmnValidationMode.PERMISSIVE;
    }
    try {
      return DmnValidationMode.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown DMN validation mode '" + value + "'. Supported values: PERMISSIVE, WARN, STRICT",
          e);
    }
  }
}
