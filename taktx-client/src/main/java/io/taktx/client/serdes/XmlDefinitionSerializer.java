/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.XmlDefinitionsDTO;
import io.taktx.serdes.JsonSerializer;

/** A JSON serializer for XmlDefinitionsDTO objects. */
public class XmlDefinitionSerializer extends JsonSerializer<XmlDefinitionsDTO> {

  /** Constructor for XmlDefinitionSerializer. */
  public XmlDefinitionSerializer() {
    super(XmlDefinitionsDTO.class);
  }
}
