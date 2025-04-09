package io.taktx.xml;

import io.taktx.bpmn.TError;
import io.taktx.dto.ErrorDTO;

public interface ErrorMapper {

  ErrorDTO map(TError tError);
}
