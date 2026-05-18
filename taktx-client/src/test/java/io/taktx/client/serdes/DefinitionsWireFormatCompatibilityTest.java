/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.taktx.dto.DefinitionsTriggerDTO;
import io.taktx.dto.DmnDefinitionsTriggerDTO;
import io.taktx.dto.XmlDefinitionsDTO;
import io.taktx.dto.XmlDmnDefinitionsDTO;
import org.junit.jupiter.api.Test;

class DefinitionsWireFormatCompatibilityTest {

  private static final ObjectMapper ENGINE_CBOR = new ObjectMapper(new CBORFactory());

  @Test
  void xmlDefinitionSerializer_producesEngineReadableCbor() throws Exception {
    XmlDefinitionsDTO dto = new XmlDefinitionsDTO("<definitions id=\"demo\"/>");

    byte[] payload;
    try (XmlDefinitionSerializer serializer = new XmlDefinitionSerializer()) {
      payload = serializer.serialize("definitions", dto);
    }

    DefinitionsTriggerDTO decoded = ENGINE_CBOR.readValue(payload, DefinitionsTriggerDTO.class);

    assertThat(decoded).isInstanceOf(XmlDefinitionsDTO.class);
    assertThat(decoded).isEqualTo(dto);
  }

  @Test
  void xmlDmnDefinitionSerializer_producesEngineReadableCbor() throws Exception {
    XmlDmnDefinitionsDTO dto = new XmlDmnDefinitionsDTO("<definitions id=\"dmn-demo\"/>");

    byte[] payload;
    try (XmlDmnDefinitionSerializer serializer = new XmlDmnDefinitionSerializer()) {
      payload = serializer.serialize("dmn-definitions", dto);
    }

    DmnDefinitionsTriggerDTO decoded =
        ENGINE_CBOR.readValue(payload, DmnDefinitionsTriggerDTO.class);

    assertThat(decoded).isInstanceOf(XmlDmnDefinitionsDTO.class);
    assertThat(decoded).isEqualTo(dto);
  }
}
