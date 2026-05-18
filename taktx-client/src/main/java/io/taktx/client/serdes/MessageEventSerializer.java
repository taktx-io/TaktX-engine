/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.MessageEventDTO;
import io.taktx.proto.MessageEventEnvelope;
import io.taktx.serdes.MessageEventProtoMapper;
import io.taktx.serdes.ProtoSerializer;
import java.util.Map;
import org.apache.kafka.common.serialization.Serializer;

/** A protobuf serializer for {@link MessageEventDTO} objects. */
public class MessageEventSerializer implements Serializer<MessageEventDTO> {

  private final ProtoSerializer<MessageEventEnvelope> delegate = new ProtoSerializer<>();

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    delegate.configure(configs, isKey);
  }

  @Override
  public byte[] serialize(String topic, MessageEventDTO data) {
    return delegate.serialize(topic, data == null ? null : MessageEventProtoMapper.toProto(data));
  }

  @Override
  public void close() {
    delegate.close();
  }
}
