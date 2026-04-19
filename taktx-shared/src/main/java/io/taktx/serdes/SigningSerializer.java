/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.Constants;
import io.taktx.security.SigningServiceHolder;
import io.taktx.security.SigningServiceHolder.SigningFunction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator {@link Serializer} that serialises the value <em>once</em> using a delegate, then
 * attaches an {@code X-TaktX-Signature} Kafka header in the same call — eliminating the
 * double-serialisation that previously occurred when signing was done before forwarding.
 *
 * <p>Kafka (both Streams and KafkaProducer) calls {@link #serialize(String, Headers, T)} when
 * headers are available, passing the actual {@link Headers} object of the outgoing record. We
 * serialise here, sign the bytes, and mutate the headers — all in one pass.
 *
 * <p>The signing function is resolved lazily from {@link SigningServiceHolder} at serialise time,
 * so the serializer does not need constructor injection and works correctly even when Kafka
 * instantiates serializers reflectively.
 *
 * <p>If no signing function is registered (signing disabled / not yet configured), the record is
 * forwarded unsigned.
 */
public class SigningSerializer<T> implements Serializer<T> {

  private static final Logger log = LoggerFactory.getLogger(SigningSerializer.class);

  private final Serializer<T> delegate;

  public SigningSerializer(Serializer<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    delegate.configure(configs, isKey);
  }

  /** Called by Kafka when headers are NOT available — delegate as-is, no signing possible. */
  @Override
  public byte[] serialize(String topic, T data) {
    return delegate.serialize(topic, data);
  }

  /**
   * Called by Kafka when headers ARE available (Streams and modern KafkaProducer). Serialises
   * {@code data} once with the delegate, signs the resulting bytes, and adds the {@code
   * X-TaktX-Signature} header — all in one serialisation pass.
   */
  @Override
  public byte[] serialize(String topic, Headers headers, T data) {
    byte[] bytes = delegate.serialize(topic, data);
    if (headers != null) {
      SigningFunction fn = SigningServiceHolder.get();
      if (fn != null) {
        byte[] payloadToSign = bytes != null ? bytes : new byte[0];
        String headerValue = fn.sign(payloadToSign);
        if (headerValue != null) {
          headers.remove(Constants.HEADER_ENGINE_SIGNATURE);
          headers.add(
              Constants.HEADER_ENGINE_SIGNATURE, headerValue.getBytes(StandardCharsets.UTF_8));
          log.trace("Signed record on topic={}", topic);
        }
      }
    }
    return bytes;
  }

  @Override
  public void close() {
    delegate.close();
  }
}
