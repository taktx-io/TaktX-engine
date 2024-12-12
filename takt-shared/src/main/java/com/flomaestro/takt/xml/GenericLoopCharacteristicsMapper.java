package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TLoopCharacteristics;
import com.flomaestro.bpmn.TMultiInstanceLoopCharacteristics;
import com.flomaestro.takt.dto.v_1_0_0.LoopCharacteristicsDTO;
import jakarta.xml.bind.JAXBElement;

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
