package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.InputOutputMappingDTO;

public interface IoMappingMapper {
  InputOutputMappingDTO map(TBaseElement tCatchEvent);
}
