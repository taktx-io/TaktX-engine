/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi.integration;

import io.taktx.Topics;
import io.taktx.client.serdes.ProcessInstanceTriggerSerializer;
import io.taktx.dto.Constants;
import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.SigningException;
import io.taktx.util.TaktPropertiesHelper;
import io.taktx.util.TaktUUIDSerializer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.LoggerFactory;

/**
 * Test helper that sends Ed25519-signed external-task responses directly to the {@code
 * process-instance-trigger} topic.
 *
 * <p>Unlike using {@link io.taktx.client.TaktXClient} with signing enabled, this class owns its own
 * {@link KafkaProducer} backed by an {@link IsolatedSigningSerializer} that captures the worker key
 * at construction time. It never reads from or writes to {@link
 * io.taktx.security.SigningServiceHolder}, so the engine's {@link
 * io.taktx.engine.security.MessageSigningService} registration is never disturbed.
 *
 * <p>This isolation is critical: in-process tests share the JVM with the engine. Any call to {@code
 * SigningServiceHolder.set(...)} would replace the engine's signing key, causing all subsequent
 * outbound engine records to carry the worker key-id instead of the engine's own key, breaking
 * signature verification on the consumer side.
 */
class WorkerResponder implements AutoCloseable {

  private final KafkaProducer<UUID, ProcessInstanceTriggerDTO> producer;
  private final String topicName;

  WorkerResponder(
      String bootstrapServers, String namespace, String keyId, String privateKeyBase64) {
    Properties props = new Properties();
    props.put("bootstrap.servers", bootstrapServers);
    props.put("taktx.engine.tenant-id", "test-tenant");
    props.put("taktx.engine.namespace", namespace);
    props.put("acks", "all");

    TaktPropertiesHelper helper = new TaktPropertiesHelper(props);
    this.topicName =
        helper.getPrefixedTopicName(Topics.PROCESS_INSTANCE_TRIGGER_TOPIC.getTopicName());

    this.producer =
        new KafkaProducer<>(
            props,
            new TaktUUIDSerializer(),
            new IsolatedSigningSerializer<>(
                new ProcessInstanceTriggerSerializer(), keyId, privateKeyBase64));
  }

  /** Sends a SUCCESS response for the given trigger, signed with the worker's Ed25519 key. */
  void respondSuccess(ExternalTaskTriggerDTO trigger, VariablesDTO variables) {
    send(
        trigger,
        new ExternalTaskResponseResultDTO(ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
        variables);
  }

  /**
   * Sends an ERROR response (allowRetry=false) for the given trigger, signed with the worker's
   * Ed25519 key. The engine will register an incident immediately.
   */
  void respondError(ExternalTaskTriggerDTO trigger, String code, String message) {
    send(
        trigger,
        new ExternalTaskResponseResultDTO(ExternalTaskResponseType.ERROR, false, code, message, 0L),
        VariablesDTO.empty());
  }

  private void send(
      ExternalTaskTriggerDTO trigger,
      ExternalTaskResponseResultDTO result,
      VariablesDTO variables) {
    ExternalTaskResponseTriggerDTO response =
        new ExternalTaskResponseTriggerDTO(
            trigger.getProcessInstanceId(), trigger.getElementInstanceIdPath(), result, variables);
    ProducerRecord<UUID, ProcessInstanceTriggerDTO> producerRecord =
        new ProducerRecord<>(topicName, trigger.getProcessInstanceId(), response);
    producer.send(producerRecord);
    producer.flush();
  }

  @Override
  public void close() {
    producer.close();
  }

  /**
   * Serializer that wraps a delegate, serialises the payload once, then attaches an Ed25519 {@code
   * X-TaktX-Signature} header using a <em>captured</em> key — without reading from or writing to
   * {@link io.taktx.security.SigningServiceHolder}.
   */
  private static final class IsolatedSigningSerializer<T> implements Serializer<T> {

    private final Serializer<T> delegate;
    private final String keyId;
    private final String privateKeyBase64;

    IsolatedSigningSerializer(Serializer<T> delegate, String keyId, String privateKeyBase64) {
      this.delegate = delegate;
      this.keyId = keyId;
      this.privateKeyBase64 = privateKeyBase64;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
      delegate.configure(configs, isKey);
    }

    @Override
    public byte[] serialize(String topic, T data) {
      return delegate.serialize(topic, data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, T data) {
      byte[] bytes = delegate.serialize(topic, data);
      if (headers != null) {
        try {
          byte[] sig = Ed25519Service.sign(bytes, privateKeyBase64);
          String headerValue = keyId + "." + Base64.getEncoder().encodeToString(sig);
          headers.remove(Constants.HEADER_ENGINE_SIGNATURE);
          headers.add(
              Constants.HEADER_ENGINE_SIGNATURE, headerValue.getBytes(StandardCharsets.UTF_8));
        } catch (SigningException e) {
          LoggerFactory.getLogger(IsolatedSigningSerializer.class)
              .error("Failed to sign worker response: {}", e.getMessage(), e);
        }
      }
      return bytes;
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
