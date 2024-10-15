package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TError;
import nl.qunit.bpmnmeister.pd.model.ErrorDTO;

public interface ErrorMapper {

  ErrorDTO map(TError tError);
}
