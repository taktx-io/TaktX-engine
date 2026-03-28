/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import io.taktx.bpmn.IoMapping;
import io.taktx.bpmn.TBaseElement;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.IoVariableMappingDTO;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class ZeebeIoMappingMapper implements IoMappingMapper {

  @Override
  public InputOutputMappingDTO map(TBaseElement tCatchEvent) {
    Set<IoVariableMappingDTO> inputMappings = new HashSet<>();
    Set<IoVariableMappingDTO> outputMappings = new HashSet<>();

    if (tCatchEvent.getExtensionElements() != null) {
      Optional<IoMapping> optCalledElement =
          ExtensionElementHelper.extractExtensionElement(
              tCatchEvent.getExtensionElements(), IoMapping.class);
      if (optCalledElement.isPresent()) {
        IoMapping ioMapping = optCalledElement.get();
        for (IoMapping.Input variableMapping : ioMapping.getInput()) {
          inputMappings.add(
              new IoVariableMappingDTO(variableMapping.getSource(), variableMapping.getTarget()));
        }
        for (IoMapping.Output variableMapping : ioMapping.getOutput()) {
          outputMappings.add(
              new IoVariableMappingDTO(variableMapping.getSource(), variableMapping.getTarget()));
        }
      }
    }
    return new InputOutputMappingDTO(inputMappings, outputMappings);
  }
}
