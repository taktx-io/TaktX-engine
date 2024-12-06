package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.pd.model.ProcessDTO;

public interface RootElementMapper {

  ProcessDTO map(TRootElement tRootElement);
}
