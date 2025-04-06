package io.taktx.xml;

import io.taktx.bpmn.IoMapping;
import io.taktx.bpmn.TBaseElement;
import io.taktx.dto.v_1_0_0.InputOutputMappingDTO;
import io.taktx.dto.v_1_0_0.IoVariableMappingDTO;
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
