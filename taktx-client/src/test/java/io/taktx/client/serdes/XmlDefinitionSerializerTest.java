/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
