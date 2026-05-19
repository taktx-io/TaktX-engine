/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.generic;

import com.google.protobuf.InvalidProtocolBufferException;
import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.engine.pd.CorrelationMessageSubscriptions;
import io.taktx.proto.CorrelationMessageSubscriptionsStoreMessage;
import io.taktx.proto.MessageEventEnvelope;
import io.taktx.serdes.MessageEventProtoMapper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/** Proto-backed serde for the correlation-message-subscription state store. */
public class CorrelationMessageSubscriptionsSerde
    implements Serde<CorrelationMessageSubscriptions> {

  private final Serializer<CorrelationMessageSubscriptions> serializer =
      (_, data) -> {
        if (data == null) {
          return null;
        }
        CorrelationMessageSubscriptionsStoreMessage.Builder builder =
            CorrelationMessageSubscriptionsStoreMessage.newBuilder();
        if (data.getInstances() != null) {
          data.getInstances().entrySet().stream()
              .sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(String::compareTo)))
              .map(Map.Entry::getValue)
              .map(MessageEventProtoMapper::toProto)
              .map(MessageEventEnvelope::getCorrSub)
              .forEach(builder::addInstances);
        }
        return builder.build().toByteArray();
      };

  private final Deserializer<CorrelationMessageSubscriptions> deserializer =
      (_, data) -> {
        if (data == null) {
          return null;
        }
        try {
          Map<String, CorrelationMessageSubscriptionDTO> instances = new LinkedHashMap<>();
          for (var instance :
              CorrelationMessageSubscriptionsStoreMessage.parseFrom(data).getInstancesList()) {
            MessageEventDTO event =
                MessageEventProtoMapper.toDto(
                    MessageEventEnvelope.newBuilder().setCorrSub(instance).build());
            CorrelationMessageSubscriptionDTO dto = (CorrelationMessageSubscriptionDTO) event;
            instances.put(dto.getCorrelationKey(), dto);
          }
          return new CorrelationMessageSubscriptions(instances);
        } catch (InvalidProtocolBufferException e) {
          throw new SerializationException(
              "Failed to deserialize CorrelationMessageSubscriptionsStoreMessage", e);
        }
      };

  @Override
  public Serializer<CorrelationMessageSubscriptions> serializer() {
    return serializer;
  }

  @Override
  public Deserializer<CorrelationMessageSubscriptions> deserializer() {
    return deserializer;
  }
}
