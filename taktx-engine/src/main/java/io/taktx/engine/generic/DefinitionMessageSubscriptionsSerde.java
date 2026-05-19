/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.generic;

import com.google.protobuf.InvalidProtocolBufferException;
import io.taktx.dto.DefinitionMessageSubscriptionDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.engine.pd.DefinitionMessageSubscriptions;
import io.taktx.proto.DefinitionMessageSubscriptionsStoreMessage;
import io.taktx.proto.MessageEventEnvelope;
import io.taktx.serdes.MessageEventProtoMapper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/** Proto-backed serde for the definition-message-subscription state store. */
public class DefinitionMessageSubscriptionsSerde implements Serde<DefinitionMessageSubscriptions> {

  private final Serializer<DefinitionMessageSubscriptions> serializer =
      (topic, data) -> {
        if (data == null) {
          return null;
        }
        DefinitionMessageSubscriptionsStoreMessage.Builder builder =
            DefinitionMessageSubscriptionsStoreMessage.newBuilder();
        if (data.getDefinitions() != null) {
          data.getDefinitions().entrySet().stream()
              .sorted(
                  Comparator.comparing(
                      entry -> entry.getKey() == null ? null : entry.getKey().getMessageName(),
                      Comparator.nullsFirst(String::compareTo)))
              .map(Map.Entry::getValue)
              .map(MessageEventProtoMapper::toProto)
              .map(MessageEventEnvelope::getDefSub)
              .forEach(builder::addDefinitions);
        }
        return builder.build().toByteArray();
      };

  private final Deserializer<DefinitionMessageSubscriptions> deserializer =
      (topic, data) -> {
        if (data == null) {
          return null;
        }
        try {
          Map<MessageEventKeyDTO, DefinitionMessageSubscriptionDTO> definitions =
              new LinkedHashMap<>();
          for (var definition :
              DefinitionMessageSubscriptionsStoreMessage.parseFrom(data).getDefinitionsList()) {
            MessageEventDTO event =
                MessageEventProtoMapper.toDto(
                    MessageEventEnvelope.newBuilder().setDefSub(definition).build());
            DefinitionMessageSubscriptionDTO dto = (DefinitionMessageSubscriptionDTO) event;
            definitions.put(dto.toMessageEventKey(), dto);
          }
          return new DefinitionMessageSubscriptions(definitions);
        } catch (InvalidProtocolBufferException e) {
          throw new SerializationException(
              "Failed to deserialize DefinitionMessageSubscriptionsStoreMessage", e);
        }
      };

  @Override
  public Serializer<DefinitionMessageSubscriptions> serializer() {
    return serializer;
  }

  @Override
  public Deserializer<DefinitionMessageSubscriptions> deserializer() {
    return deserializer;
  }
}
