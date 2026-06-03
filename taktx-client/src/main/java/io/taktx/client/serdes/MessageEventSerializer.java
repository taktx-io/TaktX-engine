/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.MessageEventDTO;
import io.taktx.security.SigningServiceHolder.SigningFunction;
import io.taktx.serdes.MessageEventProtoMapper;
import io.taktx.serdes.ProtoSigningSerializer;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

/** A protobuf serializer for {@link MessageEventDTO} objects. */
public class MessageEventSerializer implements Serializer<MessageEventDTO> {

  private final ProtoSigningSerializer<MessageEventDTO> delegate;

  public MessageEventSerializer() {
    this(null);
  }

  public MessageEventSerializer(Supplier<SigningFunction> signingFunctionSupplier) {
    this.delegate =
        new ProtoSigningSerializer<>(MessageEventProtoMapper::toProto, signingFunctionSupplier);
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    delegate.configure(configs, isKey);
  }

  @Override
  public byte[] serialize(String topic, MessageEventDTO data) {
    return delegate.serialize(topic, data);
  }

  @Override
  public byte[] serialize(String topic, Headers headers, MessageEventDTO data) {
    return delegate.serialize(topic, headers, data);
  }

  @Override
  public void close() {
    delegate.close();
  }
}
