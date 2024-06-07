package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.pd.model.InputOutputMapping;

public interface IoMappingMapper {
  InputOutputMapping map(TBaseElement tCatchEvent);
}
