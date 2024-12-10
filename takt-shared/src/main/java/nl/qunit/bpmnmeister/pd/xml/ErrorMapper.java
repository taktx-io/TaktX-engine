package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TError;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ErrorDTO;

public interface ErrorMapper {

  ErrorDTO map(TError tError);
}
