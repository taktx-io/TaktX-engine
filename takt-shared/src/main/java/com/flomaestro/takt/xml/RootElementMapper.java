package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TRootElement;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDTO;

public interface RootElementMapper {

  ProcessDTO map(TRootElement tRootElement);
}
