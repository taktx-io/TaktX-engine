/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import io.taktx.bpmn.TIntermediateThrowEvent;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.MessageIntermediateThrowEventDTO;

public class GenericMessageIntermediateThrowEventMapper
    implements MessageIntermediateThrowEventMapper {
  @Override
  public MessageIntermediateThrowEventDTO map(
      TIntermediateThrowEvent endEvent, String parentId, InputOutputMappingDTO ioMapping) {
    throw new UnsupportedOperationException(
        "GenericMessageIntermediateThrowEventMapper does not support mapping for tIntermediateThrowEvent with message definition: "
            + endEvent);
  }
}
