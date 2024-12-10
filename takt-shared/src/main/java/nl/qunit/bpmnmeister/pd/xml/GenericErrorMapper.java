package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TError;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ErrorDTO;

public class GenericErrorMapper implements ErrorMapper {

  @Override
  public ErrorDTO map(TError tError) {
    return new ErrorDTO(tError.getId(), tError.getName(), tError.getErrorCode());
  }
}
