package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.pd.model.InputOutputMappingDTO;

public interface IoMappingMapper {
  InputOutputMappingDTO map(TBaseElement tCatchEvent);
}
