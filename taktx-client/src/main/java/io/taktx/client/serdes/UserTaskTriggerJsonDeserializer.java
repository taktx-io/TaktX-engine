/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.serdes.UserTaskTriggerProtoDeserializer;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Backward-compatible alias for the protobuf user-task trigger deserializer.
 *
 * <p>The class name is kept for existing client configuration and tests while the wire format is
 * now protobuf.
 */
public class UserTaskTriggerJsonDeserializer implements Deserializer<UserTaskTriggerDTO> {

  private final UserTaskTriggerProtoDeserializer delegate = new UserTaskTriggerProtoDeserializer();

  /**
   * Returns the DTO class produced by this compatibility alias.
   *
   * @return {@link UserTaskTriggerDTO}.class
   */
  public Class<UserTaskTriggerDTO> getClazz() {
    return UserTaskTriggerDTO.class;
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    delegate.configure(configs, isKey);
  }

  @Override
  public UserTaskTriggerDTO deserialize(String topic, byte[] data) {
    return delegate.deserialize(topic, data);
  }

  @Override
  public void close() {
    delegate.close();
  }
}
