/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.DmnDefinitionKey;
import io.taktx.serdes.JsonDeserializer;

/** A JSON deserializer for {@link DmnDefinitionKey} objects. */
public class DmnDefinitionKeyJsonDeserializer extends JsonDeserializer<DmnDefinitionKey> {

  /** Constructor for DmnDefinitionKeyJsonDeserializer. */
  public DmnDefinitionKeyJsonDeserializer() {
    super(DmnDefinitionKey.class, false);
  }
}
