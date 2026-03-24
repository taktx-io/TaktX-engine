/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.taktx.Topics;
import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import io.taktx.util.TaktPropertiesHelper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes a {@link SigningKeyDTO} to the {@code taktx-signing-keys} compacted topic.
 *
 * <p>Framework-agnostic (plain Java, no CDI annotations) — intended for reuse by the engine
 * (Quarkus), worker clients (plain Java / Spring), and the Platform team's ingester. The compacted
 * topic ensures idempotent re-publishing: restarting a process simply overwrites the same key.
 *
 * <p>The Kafka record key is the {@code keyId} so compaction retains one entry per key.
 *
 * <p>Follows the same pattern as {@code ProcessDefinitionDeployer} and {@code MessageEventSender}:
 * accepts a {@link TaktPropertiesHelper} so that all Kafka auth/security properties (SASL, SSL,
 * etc.) flow through automatically, rather than building a bare-bones {@code Properties} object
 * with only {@code bootstrap.servers}.
 */
public class SigningKeyRegistrar {

  private static final String DEFAULT_ED25519_ALGORITHM = "Ed25519";
  private static final Logger log = LoggerFactory.getLogger(SigningKeyRegistrar.class);
  private static final ObjectMapper CBOR =
      new ObjectMapper(new CBORFactory()).registerModule(new JavaTimeModule());

  /**
   * Returns the canonical payload bytes that the platform root key signs when countersigning a key
   * registration.
   *
   * <p>Format: {@code keyId|publicKeyBase64|algorithm|owner|role} — pipe-delimited UTF-8. The
   * {@code role} token is the {@link KeyRole#name()} string (e.g. {@code "ENGINE"}, {@code
   * "CLIENT"}).
   *
   * <p>Reproducible in a shell script:
   *
   * <pre>{@code
   * printf '%s|%s|%s|%s|%s' "$KEY_ID" "$PUBLIC_KEY_BASE64" "$ALGORITHM" "$OWNER" "$ROLE"
   * }</pre>
   *
   * <p>To sign and base64-encode (for {@code TAKTX_ENGINE_KEY_REGISTRATION_SIGNATURE} / {@code
   * TAKTX_SIGNING_REGISTRATION_SIGNATURE}):
   *
   * <pre>{@code
   * printf '%s|%s|%s|%s|%s' "$KEY_ID" "$PUBLIC_KEY_BASE64" "$ALGORITHM" "$OWNER" "$ROLE" \
   *   | openssl dgst -sha256 -sign platform-private.pem \
   *   | base64
   * }</pre>
   *
   * See {@code scripts/generate_trust_anchor.sh} for the complete operator workflow.
   *
   * @param key a fully-populated {@link SigningKeyDTO} (keyId, publicKeyBase64, algorithm, owner,
   *     and role must all be non-null)
   * @return UTF-8 bytes of the pipe-delimited canonical payload
   */
  public static byte[] computeCanonicalPayload(SigningKeyDTO key) {
    String payload =
        key.getKeyId()
            + "|"
            + key.getPublicKeyBase64()
            + "|"
            + key.getAlgorithm()
            + "|"
            + key.getOwner()
            + "|"
            + key.effectiveRole().name();
    return payload.getBytes(StandardCharsets.UTF_8);
  }

  private final TaktPropertiesHelper taktPropertiesHelper;

  /**
   * Creates an instance-based registrar that uses {@link TaktPropertiesHelper} to build the Kafka
   * producer — ensuring all authentication / TLS properties are inherited automatically.
   *
   * @param taktPropertiesHelper the properties helper from the running {@code TaktXClient}
   */
  public SigningKeyRegistrar(TaktPropertiesHelper taktPropertiesHelper) {
    this.taktPropertiesHelper = taktPropertiesHelper;
  }

  // ── Instance methods ────────────────────────────────────────────────────────

  /**
   * Publishes the given public key with the default Ed25519 algorithm, {@link KeyRole#CLIENT} role,
   * and no registration signature (community mode).
   */
  public void publishPublicKey(String keyId, String publicKeyBase64, String owner) {
    publishPublicKey(keyId, publicKeyBase64, owner, DEFAULT_ED25519_ALGORITHM);
  }

  /**
   * Publishes the given public key with an explicit algorithm label, {@link KeyRole#CLIENT} role,
   * and no registration signature (community mode).
   */
  public void publishPublicKey(
      String keyId, String publicKeyBase64, String owner, String algorithm) {
    publishPublicKey(keyId, publicKeyBase64, owner, algorithm, KeyRole.CLIENT);
  }

  /** Publishes the given public key with an explicit algorithm label and role (community mode). */
  public void publishPublicKey(
      String keyId, String publicKeyBase64, String owner, String algorithm, KeyRole role) {
    publishPublicKey(keyId, publicKeyBase64, owner, algorithm, role, null);
  }

  /**
   * Publishes the given public key with an explicit algorithm label, role, and platform
   * countersignature.
   *
   * <p>Use this overload in anchored mode. The {@code registrationSignature} must be the
   * base64-encoded RSA/SHA-256 signature produced by the platform root private key over {@link
   * #computeCanonicalPayload(SigningKeyDTO)} for this key. When {@code null}, the key is published
   * without a countersignature — valid only in community mode.
   */
  public void publishPublicKey(
      String keyId,
      String publicKeyBase64,
      String owner,
      String algorithm,
      KeyRole role,
      String registrationSignature) {
    String topic =
        taktPropertiesHelper.getPrefixedTopicName(Topics.SIGNING_KEYS_TOPIC.getTopicName());
    publishPublicKey(
        taktPropertiesHelper,
        topic,
        keyId,
        publicKeyBase64,
        owner,
        algorithm,
        role,
        registrationSignature);
  }

  // ── Static helpers ──────────────────────────────────────────────────────────

  /**
   * Publishes the given public key — defaults to Ed25519 algorithm, {@link KeyRole#CLIENT} role,
   * and no registration signature (community mode).
   */
  public static void publishPublicKey(
      String bootstrapServers, String topic, String keyId, String publicKeyBase64, String owner) {
    publishPublicKey(
        bootstrapServers, topic, keyId, publicKeyBase64, owner, DEFAULT_ED25519_ALGORITHM);
  }

  /** Publishes the given public key with an explicit algorithm and {@link KeyRole#CLIENT} role. */
  public static void publishPublicKey(
      String bootstrapServers,
      String topic,
      String keyId,
      String publicKeyBase64,
      String owner,
      String algorithm) {
    publishPublicKey(
        bootstrapServers, topic, keyId, publicKeyBase64, owner, algorithm, KeyRole.CLIENT);
  }

  /** Publishes the given public key with an explicit algorithm and role (community mode). */
  public static void publishPublicKey(
      String bootstrapServers,
      String topic,
      String keyId,
      String publicKeyBase64,
      String owner,
      String algorithm,
      KeyRole role) {
    publishPublicKey(bootstrapServers, topic, keyId, publicKeyBase64, owner, algorithm, role, null);
  }

  /**
   * Publishes the given public key with an explicit algorithm, role, and platform countersignature.
   *
   * <p>In anchored mode, {@code registrationSignature} must be the base64-encoded RSA/SHA-256
   * signature produced by the platform root private key over {@link
   * #computeCanonicalPayload(SigningKeyDTO)} for this key. Pass {@code null} in community mode (no
   * platform root key configured).
   */
  public static void publishPublicKey(
      String bootstrapServers,
      String topic,
      String keyId,
      String publicKeyBase64,
      String owner,
      String algorithm,
      KeyRole role,
      String registrationSignature) {

    Properties props = new Properties();
    props.put("bootstrap.servers", bootstrapServers);
    props.put("acks", "all");
    props.put("retries", "3");
    // Fail fast when no broker is reachable (e.g. unit tests, broker not yet started).
    // Without these the producer retries indefinitely and flush() blocks forever.
    props.put("max.block.ms", "5000");
    props.put("delivery.timeout.ms", "5000");
    props.put("request.timeout.ms", "3000");

    doPublish(
        props,
        new StringSerializer(),
        new ByteArraySerializer(),
        topic,
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64(publicKeyBase64)
            .algorithm(algorithm)
            .status(KeyStatus.ACTIVE)
            .owner(owner)
            .role(role != null ? role : KeyRole.CLIENT)
            .registrationSignature(registrationSignature)
            .build());
  }

  /**
   * Publishes the given public key using TaktPropertiesHelper-derived producer properties, explicit
   * role, and optional registration signature.
   */
  static void publishPublicKey(
      TaktPropertiesHelper taktPropertiesHelper,
      String topic,
      String keyId,
      String publicKeyBase64,
      String owner,
      String algorithm,
      KeyRole role,
      String registrationSignature) {

    doPublish(
        taktPropertiesHelper.getKafkaProducerProperties(),
        new StringSerializer(),
        new ByteArraySerializer(),
        topic,
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64(publicKeyBase64)
            .algorithm(algorithm)
            .status(KeyStatus.ACTIVE)
            .owner(owner)
            .role(role != null ? role : KeyRole.CLIENT)
            .registrationSignature(registrationSignature)
            .build());
  }

  // ── Package-private DTO builder (for testing) ──────────────────────────────

  /**
   * Constructs the {@link SigningKeyDTO} that {@link #publishPublicKey} would publish, without
   * connecting to Kafka. Visible for unit tests in the same package.
   */
  static SigningKeyDTO buildSigningKeyDto(
      String keyId,
      String publicKeyBase64,
      String owner,
      String algorithm,
      KeyRole role,
      String registrationSignature) {
    return SigningKeyDTO.builder()
        .keyId(keyId)
        .publicKeyBase64(publicKeyBase64)
        .algorithm(algorithm != null ? algorithm : DEFAULT_ED25519_ALGORITHM)
        .status(KeyStatus.ACTIVE)
        .owner(owner)
        .role(role != null ? role : KeyRole.CLIENT)
        .registrationSignature(registrationSignature)
        .build();
  }

  // ── Status change / revocation ─────────────────────────────────────────────

  /**
   * Publishes a status change for an existing key. Used for key rotation (ACTIVE → TRUSTED) and
   * revocation (any → REVOKED).
   *
   * <p>The compacted topic will retain only the latest record per keyId, so this overwrites the
   * previous entry. The original {@code createdAt} and public key material are preserved so that
   * in-flight signature verification can still succeed during the drain window.
   */
  public void publishKeyStatusChange(SigningKeyDTO updatedKey) {
    String topic =
        taktPropertiesHelper.getPrefixedTopicName(Topics.SIGNING_KEYS_TOPIC.getTopicName());
    doPublish(
        taktPropertiesHelper.getKafkaProducerProperties(),
        new StringSerializer(),
        new ByteArraySerializer(),
        topic,
        updatedKey);
  }

  /**
   * Publishes a REVOKED status for the given key. Requires the full key entry so the compacted
   * topic retains the public key material for audit purposes.
   */
  public static void revokeKey(String bootstrapServers, String topic, SigningKeyDTO existingKey) {
    SigningKeyDTO revoked =
        SigningKeyDTO.builder()
            .keyId(existingKey.getKeyId())
            .publicKeyBase64(existingKey.getPublicKeyBase64())
            .algorithm(existingKey.getAlgorithm())
            .createdAt(existingKey.getCreatedAt())
            .status(KeyStatus.REVOKED)
            .owner(existingKey.getOwner())
            .role(existingKey.effectiveRole())
            .registrationSignature(existingKey.getRegistrationSignature())
            .build();

    Properties props = new Properties();
    props.put("bootstrap.servers", bootstrapServers);
    props.put("acks", "all");
    props.put("retries", "3");
    props.put("max.block.ms", "5000");
    props.put("delivery.timeout.ms", "5000");
    props.put("request.timeout.ms", "3000");

    doPublish(props, new StringSerializer(), new ByteArraySerializer(), topic, revoked);
  }

  /**
   * Publishes the given {@link SigningKeyDTO} as-is (preserving its status, role, and createdAt).
   * Use this for status transitions such as ACTIVE → TRUSTED (key retirement during rotation).
   */
  public static void publishKeyWithStatus(
      String bootstrapServers, String topic, SigningKeyDTO key) {
    Properties props = new Properties();
    props.put("bootstrap.servers", bootstrapServers);
    props.put("acks", "all");
    props.put("retries", "3");
    props.put("max.block.ms", "5000");
    props.put("delivery.timeout.ms", "5000");
    props.put("request.timeout.ms", "3000");

    doPublish(props, new StringSerializer(), new ByteArraySerializer(), topic, key);
  }

  private static void doPublish(
      Properties producerProps,
      StringSerializer keySerializer,
      ByteArraySerializer valueSerializer,
      String topic,
      SigningKeyDTO dto) {
    String keyId = dto.getKeyId();
    SigningKeyDTO keyToPublish =
        SigningKeyDTO.builder()
            .keyId(dto.getKeyId())
            .publicKeyBase64(dto.getPublicKeyBase64())
            .algorithm(dto.getAlgorithm())
            .createdAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : Instant.now())
            .status(dto.getStatus() != null ? dto.getStatus() : KeyStatus.ACTIVE)
            .owner(dto.getOwner())
            .role(dto.effectiveRole())
            .registrationSignature(dto.getRegistrationSignature())
            .build();

    try (KafkaProducer<String, byte[]> producer =
        new KafkaProducer<>(producerProps, keySerializer, valueSerializer)) {
      byte[] valueBytes = CBOR.writeValueAsBytes(keyToPublish);
      producer.send(new ProducerRecord<>(topic, keyId, valueBytes));
      producer.flush();
      log.info(
          "✅ Published signing key: keyId={} owner={} algorithm={} role={} status={} topic={}",
          keyId,
          keyToPublish.getOwner(),
          keyToPublish.getAlgorithm(),
          keyToPublish.getRole(),
          keyToPublish.getStatus(),
          topic);
    } catch (Exception e) {
      log.error(
          "Failed to publish signing key keyId={} to {}: {}", keyId, topic, e.getMessage(), e);
      throw new IllegalStateException("Failed to publish signing key", e);
    }
  }

  /**
   * Derives the public key from a key pair, publishes it with {@link KeyRole#CLIENT}, and returns
   * the {@code publicKeyBase64} for further use.
   */
  public static String publishFromKeyPair(
      String bootstrapServers, String topic, String keyId, KeyPair keyPair, String owner) {
    return publishFromKeyPair(bootstrapServers, topic, keyId, keyPair, owner, KeyRole.CLIENT);
  }

  /**
   * Derives the public key from a key pair, publishes it with the given role, and returns the
   * {@code publicKeyBase64} for further use.
   */
  public static String publishFromKeyPair(
      String bootstrapServers,
      String topic,
      String keyId,
      KeyPair keyPair,
      String owner,
      KeyRole role) {
    return publishFromKeyPair(bootstrapServers, topic, keyId, keyPair, owner, role, null);
  }

  /**
   * Derives the public key from a key pair, publishes it with the given role and platform
   * countersignature, and returns the {@code publicKeyBase64} for further use.
   */
  public static String publishFromKeyPair(
      String bootstrapServers,
      String topic,
      String keyId,
      KeyPair keyPair,
      String owner,
      KeyRole role,
      String registrationSignature) {
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());
    publishPublicKey(
        bootstrapServers,
        topic,
        keyId,
        publicKeyBase64,
        owner,
        DEFAULT_ED25519_ALGORITHM,
        role,
        registrationSignature);
    return publicKeyBase64;
  }
}
