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
import io.taktx.dto.SigningKeyDTO;
import io.taktx.dto.SigningKeyDTO.KeyStatus;
import io.taktx.util.TaktPropertiesHelper;
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

  private static final Logger log = LoggerFactory.getLogger(SigningKeyRegistrar.class);
  private static final ObjectMapper CBOR =
      new ObjectMapper(new CBORFactory()).registerModule(new JavaTimeModule());

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

  /**
   * Publishes the given public key to the {@code taktx-signing-keys} topic using the Kafka producer
   * properties from the {@link TaktPropertiesHelper} supplied at construction time.
   *
   * @param keyId unique identifier for this key, e.g. {@code "engine-2026-001"}
   * @param publicKeyBase64 base64-encoded X.509/SubjectPublicKeyInfo DER of the Ed25519 public key
   * @param owner human-readable label, e.g. {@code "engine"} or {@code "worker-billing"}
   */
  public void publishPublicKey(String keyId, String publicKeyBase64, String owner) {
    String topic =
        taktPropertiesHelper.getPrefixedTopicName(Topics.SIGNING_KEYS_TOPIC.getTopicName());
    publishPublicKey(taktPropertiesHelper, topic, keyId, publicKeyBase64, owner);
  }

  // ── Static helpers (kept for backward-compat and for callers without a running client) ──

  /**
   * Publishes the given public key to the {@code taktx-signing-keys} topic.
   *
   * <p>Prefer the instance method {@link #publishPublicKey(String, String, String)} when a {@link
   * TaktPropertiesHelper} is available, so that Kafka auth/TLS settings are inherited
   * automatically.
   *
   * @param bootstrapServers Kafka bootstrap.servers value
   * @param topic fully-qualified (namespaced) topic name
   * @param keyId unique identifier for this key, e.g. {@code "engine-2026-001"}
   * @param publicKeyBase64 base64-encoded X.509/SubjectPublicKeyInfo DER of the Ed25519 public key
   * @param owner human-readable label, e.g. {@code "engine"} or {@code "worker-billing"}
   */
  public static void publishPublicKey(
      String bootstrapServers, String topic, String keyId, String publicKeyBase64, String owner) {

    Properties props = new Properties();
    props.put("bootstrap.servers", bootstrapServers);
    props.put("acks", "all");
    props.put("retries", "3");
    // Fail fast when no broker is reachable (e.g. unit tests, broker not yet started).
    // Without these the producer retries indefinitely and flush() blocks forever.
    props.put("max.block.ms", "5000");
    props.put("delivery.timeout.ms", "5000");
    props.put("request.timeout.ms", "3000");
    props.put("key.serializer", StringSerializer.class.getName());
    props.put("value.serializer", ByteArraySerializer.class.getName());

    doPublish(props, topic, keyId, publicKeyBase64, owner);
  }

  /**
   * Publishes the given public key using the producer properties derived from {@code
   * taktPropertiesHelper}, which ensures auth/TLS settings are forwarded correctly.
   */
  static void publishPublicKey(
      TaktPropertiesHelper taktPropertiesHelper,
      String topic,
      String keyId,
      String publicKeyBase64,
      String owner) {

    Properties props =
        taktPropertiesHelper.getKafkaProducerProperties(
            StringSerializer.class, ByteArraySerializer.class);
    doPublish(props, topic, keyId, publicKeyBase64, owner);
  }

  private static void doPublish(
      Properties producerProps, String topic, String keyId, String publicKeyBase64, String owner) {

    SigningKeyDTO dto =
        SigningKeyDTO.builder()
            .keyId(keyId)
            .publicKeyBase64(publicKeyBase64)
            .algorithm("Ed25519")
            .createdAt(Instant.now())
            .status(KeyStatus.ACTIVE)
            .owner(owner)
            .build();

    try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(producerProps)) {
      byte[] valueBytes = CBOR.writeValueAsBytes(dto);
      producer.send(new ProducerRecord<>(topic, keyId, valueBytes));
      producer.flush();
      log.info("✅ Published signing key: keyId={} owner={} topic={}", keyId, owner, topic);
    } catch (Exception e) {
      log.error(
          "Failed to publish signing key keyId={} to {}: {}", keyId, topic, e.getMessage(), e);
      throw new IllegalStateException("Failed to publish signing key", e);
    }
  }

  /**
   * Derives the public key from a key pair generated by {@link SigningKeyGenerator}, publishes it,
   * and returns the {@code publicKeyBase64} for further use.
   */
  public static String publishFromKeyPair(
      String bootstrapServers, String topic, String keyId, KeyPair keyPair, String owner) {
    String publicKeyBase64 = SigningKeyGenerator.encodePublicKey(keyPair.getPublic());
    publishPublicKey(bootstrapServers, topic, keyId, publicKeyBase64, owner);
    return publicKeyBase64;
  }
}
