package io.taktx.xml;

import io.taktx.bpmn.TLoopCharacteristics;
import io.taktx.dto.v_1_0_0.LoopCharacteristicsDTO;
import jakarta.xml.bind.JAXBElement;

public interface LoopCharacteristicsMapper {
  LoopCharacteristicsDTO map(JAXBElement<? extends TLoopCharacteristics> loopCharacteristics);
}
