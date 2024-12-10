package nl.qunit.bpmnmeister.pd.xml;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import nl.qunit.bpmnmeister.bpmn.IoMapping;
import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.IoVariableMappingDTO;

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
