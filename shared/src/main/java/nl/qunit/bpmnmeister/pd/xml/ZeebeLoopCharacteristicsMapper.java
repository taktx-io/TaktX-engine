package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.Optional;
import nl.qunit.bpmnmeister.bpmn.TLoopCharacteristics;
import nl.qunit.bpmnmeister.bpmn.TMultiInstanceLoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;

public class ZeebeLoopCharacteristicsMapper implements LoopCharacteristicsMapper {

  public LoopCharacteristics map(
      JAXBElement<? extends TLoopCharacteristics> loopCharacteristics) {
    if (loopCharacteristics != null) {
      TLoopCharacteristics tLoopCharacteristics = loopCharacteristics.getValue();
      if (tLoopCharacteristics instanceof TMultiInstanceLoopCharacteristics multiInstanceLoopCharacteristics) {
        Optional<nl.qunit.bpmnmeister.bpmn.LoopCharacteristics> optLoop = ExtensionElementHelper.extractExtensionElement(tLoopCharacteristics.getExtensionElements(),
            nl.qunit.bpmnmeister.bpmn.LoopCharacteristics.class);
        if (optLoop.isPresent()) {
          nl.qunit.bpmnmeister.bpmn.LoopCharacteristics zeebeLoop = optLoop.get();
          return new LoopCharacteristics(
              multiInstanceLoopCharacteristics.isIsSequential(),
              zeebeLoop.getInputCollection(),
              zeebeLoop.getInputElement(),
              zeebeLoop.getOutputCollection(),
              zeebeLoop.getOutputElement());
        }
      }
    }
    return LoopCharacteristics.NONE;
  }
}
