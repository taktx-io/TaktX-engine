package io.taktx.xml;

import io.taktx.bpmn.TLoopCharacteristics;
import io.taktx.dto.LoopCharacteristicsDTO;
import jakarta.xml.bind.JAXBElement;

public interface LoopCharacteristicsMapper {
  LoopCharacteristicsDTO map(JAXBElement<? extends TLoopCharacteristics> loopCharacteristics);
}
