package io.taktx.xml;

import io.taktx.bpmn.TError;
import io.taktx.dto.v_1_0_0.ErrorDTO;

public interface ErrorMapper {

  ErrorDTO map(TError tError);
}
