package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TError;
import com.flomaestro.takt.dto.v_1_0_0.ErrorDTO;

public interface ErrorMapper {

  ErrorDTO map(TError tError);
}
