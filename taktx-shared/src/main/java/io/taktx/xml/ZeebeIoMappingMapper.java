/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
