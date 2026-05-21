/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.MessageLite;
import io.taktx.dto.Constants;
import io.taktx.security.SigningServiceHolder;
import io.taktx.security.SigningServiceHolder.SigningFunction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator {@link Serializer} for protobuf-backed payloads.
 *
 * <p>The supplied mapper converts the logical value into a protobuf message, which is then encoded
 * via {@link MessageLite#toByteArray()}. When Kafka provides headers, the serialized bytes are
 * signed once and the {@code tx-sig} header is stamped using the existing engine signing holder.
 */
public class ProtoSigningSerializer<T> implements Serializer<T> {

  private static final Logger log = LoggerFactory.getLogger(ProtoSigningSerializer.class);

  private final Function<T, ? extends MessageLite> protoMapper;

  public ProtoSigningSerializer(Function<T, ? extends MessageLite> protoMapper) {
    this.protoMapper = Objects.requireNonNull(protoMapper, "protoMapper must not be null");
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    // no-op
  }

  @Override
  public byte[] serialize(String topic, T data) {
    return toBytes(data);
  }

  @Override
  public byte[] serialize(String topic, Headers headers, T data) {
    byte[] bytes = toBytes(data);
    if (headers != null) {
      SigningFunction fn = SigningServiceHolder.get();
      if (fn != null) {
        byte[] payloadToSign = bytes != null ? bytes : new byte[0];
        String headerValue = fn.sign(payloadToSign);
        if (headerValue != null) {
          headers.remove(Constants.HEADER_ENGINE_SIGNATURE);
          headers.add(
              Constants.HEADER_ENGINE_SIGNATURE, headerValue.getBytes(StandardCharsets.UTF_8));
          log.trace("Signed proto record on topic={}", topic);
        }
      }
    }
    return bytes;
  }

  @SuppressWarnings("java:S1168")
  private byte[] toBytes(T data) {
    if (data == null) {
      return null;
    }
    MessageLite message = protoMapper.apply(data);
    return message == null ? null : message.toByteArray();
  }

  @Override
  public void close() {
    // no-op
  }
}
