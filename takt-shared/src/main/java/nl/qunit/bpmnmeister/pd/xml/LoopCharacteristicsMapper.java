package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import nl.qunit.bpmnmeister.bpmn.TLoopCharacteristics;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.LoopCharacteristicsDTO;

public interface LoopCharacteristicsMapper {
  LoopCharacteristicsDTO map(JAXBElement<? extends TLoopCharacteristics> loopCharacteristics);
}
