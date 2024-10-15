package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TError;
import nl.qunit.bpmnmeister.pd.model.ErrorDTO;

public class GenericErrorMapper implements ErrorMapper {

  @Override
  public ErrorDTO map(TError tError) {
    return new ErrorDTO(
        tError.getId(), tError.getName(), tError.getErrorCode());
  }
}
