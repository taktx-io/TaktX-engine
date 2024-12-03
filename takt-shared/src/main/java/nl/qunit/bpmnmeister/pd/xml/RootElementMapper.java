package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.pd.model.Process;

public interface RootElementMapper {
  Process map(TRootElement tRootElement);
}
