package io.taktx.xml;

import io.taktx.bpmn.TRootElement;
import io.taktx.dto.v_1_0_0.ProcessDTO;

public interface RootElementMapper {

  ProcessDTO map(TRootElement tRootElement);
}
