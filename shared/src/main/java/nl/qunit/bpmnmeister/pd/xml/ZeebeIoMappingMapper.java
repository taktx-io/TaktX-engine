package nl.qunit.bpmnmeister.pd.xml;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import nl.qunit.bpmnmeister.bpmn.IoMapping;
import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.pd.model.InputOutputMapping;
import nl.qunit.bpmnmeister.pd.model.IoVariableMapping;

public class ZeebeIoMappingMapper implements IoMappingMapper {

  @Override
  public InputOutputMapping map(TBaseElement tCatchEvent) {
    Set<IoVariableMapping> inputMappings = new HashSet<>();
    Set<IoVariableMapping> outputMappings = new HashSet<>();

    if (tCatchEvent.getExtensionElements() != null) {
      Optional<IoMapping> optCalledElement =
          ExtensionElementHelper.extractExtensionElement(
              tCatchEvent.getExtensionElements(), IoMapping.class);
      if (optCalledElement.isPresent()) {
        IoMapping ioMapping = optCalledElement.get();
        for (IoMapping.Input variableMapping : ioMapping.getInput()) {
          inputMappings.add(
              new IoVariableMapping(variableMapping.getSource(), variableMapping.getTarget()));
        }
        for (IoMapping.Output variableMapping : ioMapping.getOutput()) {
          outputMappings.add(
              new IoVariableMapping(variableMapping.getSource(), variableMapping.getTarget()));
        }
      }
    }
    return new InputOutputMapping(inputMappings, outputMappings);
  }
}
