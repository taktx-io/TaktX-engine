package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.LoopCharacteristics;
import com.flomaestro.bpmn.TLoopCharacteristics;
import com.flomaestro.bpmn.TMultiInstanceLoopCharacteristics;
import com.flomaestro.takt.dto.v_1_0_0.LoopCharacteristicsDTO;
import jakarta.xml.bind.JAXBElement;
import java.util.Optional;

public class ZeebeLoopCharacteristicsMapper implements LoopCharacteristicsMapper {

  public LoopCharacteristicsDTO map(
      JAXBElement<? extends TLoopCharacteristics> loopCharacteristics) {
    if (loopCharacteristics != null) {
      TLoopCharacteristics tLoopCharacteristics = loopCharacteristics.getValue();
      if (tLoopCharacteristics
          instanceof TMultiInstanceLoopCharacteristics multiInstanceLoopCharacteristics) {
        Optional<LoopCharacteristics> optLoop =
            ExtensionElementHelper.extractExtensionElement(
                tLoopCharacteristics.getExtensionElements(), LoopCharacteristics.class);
        if (optLoop.isPresent()) {
          LoopCharacteristics zeebeLoop = optLoop.get();
          return new LoopCharacteristicsDTO(
              multiInstanceLoopCharacteristics.isIsSequential(),
              zeebeLoop.getInputCollection(),
              zeebeLoop.getInputElement(),
              zeebeLoop.getOutputCollection(),
              zeebeLoop.getOutputElement());
        }
      }
    }
    return LoopCharacteristicsDTO.NONE;
  }
}
