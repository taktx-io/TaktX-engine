/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.XmlDmnDefinitionsDTO;
import org.junit.jupiter.api.Test;

class MiscSerdesTest {

  @Test
  void topicMetaJsonDeserializer_hasCorrectClass() {
    try (TopicMetaJsonDeserializer d = new TopicMetaJsonDeserializer()) {
      assertThat(d.getClazz()).isEqualTo(TopicMetaDTO.class);
    }
  }

  @Test
  void userTaskTriggerJsonDeserializer_hasCorrectClass() {
    try (UserTaskTriggerJsonDeserializer d = new UserTaskTriggerJsonDeserializer()) {
      assertThat(d.getClazz()).isEqualTo(UserTaskTriggerDTO.class);
    }
  }

  @Test
  void xmlDmnDefinitionSerializer_hasCorrectClass() {
    try (XmlDmnDefinitionSerializer s = new XmlDmnDefinitionSerializer()) {
      assertThat(s.getClazz()).isEqualTo(XmlDmnDefinitionsDTO.class);
    }
  }

  @Test
  void faultTolerantExternalTaskTriggerDeserializer_hasCorrectClass() {
    try (FaultTolerantExternalTaskTriggerDeserializer d =
        new FaultTolerantExternalTaskTriggerDeserializer()) {
      assertThat(d.getClazz()).isEqualTo(io.taktx.dto.ExternalTaskTriggerDTO.class);
    }
  }

  @Test
  void faultTolerantUserTaskTriggerDeserializer_hasCorrectClass() {
    try (FaultTolerantUserTaskTriggerDeserializer d =
        new FaultTolerantUserTaskTriggerDeserializer()) {
      assertThat(d.getClazz()).isEqualTo(UserTaskTriggerDTO.class);
    }
  }
}
