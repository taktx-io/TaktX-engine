/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.ProcessDefinitionKey;
import org.apache.kafka.common.serialization.Deserializer;

/** Deserializes process-definition keys from the current binary key format. */
public class ProcessDefinitionKeyDeserializer implements Deserializer<ProcessDefinitionKey> {

  @Override
  public ProcessDefinitionKey deserialize(String topic, byte[] data) {
    return data == null ? null : io.taktx.util.ProcessDefinitionKeyDeserializer.fromBytes(data);
  }
}

