package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TLoopCharacteristics;
import com.flomaestro.takt.dto.v_1_0_0.LoopCharacteristicsDTO;
import jakarta.xml.bind.JAXBElement;

public interface LoopCharacteristicsMapper {
  LoopCharacteristicsDTO map(JAXBElement<? extends TLoopCharacteristics> loopCharacteristics);
}
