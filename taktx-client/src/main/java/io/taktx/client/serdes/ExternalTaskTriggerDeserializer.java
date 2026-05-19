/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.serdes.ExternalTaskTriggerProtoDeserializer;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;

/** Client deserializer for protobuf-backed external-task triggers. */
public class ExternalTaskTriggerDeserializer implements Deserializer<ExternalTaskTriggerDTO> {

  private final ExternalTaskTriggerProtoDeserializer delegate =
      new ExternalTaskTriggerProtoDeserializer();

  public Class<ExternalTaskTriggerDTO> getClazz() {
    return ExternalTaskTriggerDTO.class;
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    delegate.configure(configs, isKey);
  }

  @Override
  public ExternalTaskTriggerDTO deserialize(String topic, byte[] data) {
    return delegate.deserialize(topic, data);
  }

  @Override
  public void close() {
    delegate.close();
  }
}


