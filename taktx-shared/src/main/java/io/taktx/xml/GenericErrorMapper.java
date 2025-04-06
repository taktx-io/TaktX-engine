package io.taktx.xml;

import io.taktx.bpmn.TError;
import io.taktx.dto.v_1_0_0.ErrorDTO;

public class GenericErrorMapper implements ErrorMapper {

  @Override
  public ErrorDTO map(TError tError) {
    return new ErrorDTO(tError.getId(), tError.getName(), tError.getErrorCode());
  }
}
