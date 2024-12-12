package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TError;
import com.flomaestro.takt.dto.v_1_0_0.ErrorDTO;

public class GenericErrorMapper implements ErrorMapper {

  @Override
  public ErrorDTO map(TError tError) {
    return new ErrorDTO(tError.getId(), tError.getName(), tError.getErrorCode());
  }
}
