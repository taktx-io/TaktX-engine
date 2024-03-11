package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import nl.qunit.bpmnmeister.bpmn.TLoopCharacteristics;
import nl.qunit.bpmnmeister.bpmn.TMultiInstanceLoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.LoopCharacteristics;

public class LoopCharacteristicsMapper {
  private LoopCharacteristicsMapper() {
    // private constructor
  }

  public static LoopCharacteristics mapLoopCharacteristics(
      JAXBElement<? extends TLoopCharacteristics> loopCharacteristics) {
    if (loopCharacteristics != null) {
      TLoopCharacteristics tLoopCharacteristics = loopCharacteristics.getValue();
      if (tLoopCharacteristics
          instanceof TMultiInstanceLoopCharacteristics multiInstanceLoopCharacteristics) {
        return new LoopCharacteristics(
            multiInstanceLoopCharacteristics.isIsSequential(),
            ((TMultiInstanceLoopCharacteristics) tLoopCharacteristics)
                .getLoopDataInputRef()
                .toString(),
            ((TMultiInstanceLoopCharacteristics) tLoopCharacteristics)
                .getInputDataItem()
                .getName());
      }
    }
    return null;
  }
}
