package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import nl.qunit.bpmnmeister.bpmn.TLoopCharacteristics;
import nl.qunit.bpmnmeister.bpmn.TMultiInstanceLoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.LoopCharacteristicsDTO;

public class GenericLoopCharacteristicsMapper implements LoopCharacteristicsMapper {

  public LoopCharacteristicsDTO map(
      JAXBElement<? extends TLoopCharacteristics> loopCharacteristics) {
    if (loopCharacteristics != null) {
      TLoopCharacteristics tLoopCharacteristics = loopCharacteristics.getValue();
      if (tLoopCharacteristics instanceof TMultiInstanceLoopCharacteristics tLoopCharacteristics1) {
        return new LoopCharacteristicsDTO(
            tLoopCharacteristics1.isIsSequential(),
            tLoopCharacteristics1.getLoopDataInputRef().toString(),
            tLoopCharacteristics1.getInputDataItem().getName(),
            tLoopCharacteristics1.getLoopDataOutputRef().toString(),
            tLoopCharacteristics1.getOutputDataItem().getName());
      }
    }
    return LoopCharacteristicsDTO.NONE;
  }
}
