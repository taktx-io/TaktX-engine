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
      if (tLoopCharacteristics instanceof TMultiInstanceLoopCharacteristics tLoopCharacteristics1) {
        return new LoopCharacteristics(
            tLoopCharacteristics1.isIsSequential(),
            tLoopCharacteristics1.getLoopDataInputRef().toString(),
            tLoopCharacteristics1.getInputDataItem().getName(),
            tLoopCharacteristics1.getLoopDataOutputRef().toString(),
            tLoopCharacteristics1.getOutputDataItem().getName());
      }
    }
    return LoopCharacteristics.NONE;
  }
}
