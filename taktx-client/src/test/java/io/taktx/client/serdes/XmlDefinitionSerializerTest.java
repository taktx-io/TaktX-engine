/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.XmlDefinitionsDTO;
import org.junit.jupiter.api.Test;

class XmlDefinitionSerializerTest {
  @Test
  void testConstruct() {
    try (XmlDefinitionSerializer serializer = new XmlDefinitionSerializer()) {
      assertThat(serializer.getClazz()).isEqualTo(XmlDefinitionsDTO.class);
    }
  }
}
